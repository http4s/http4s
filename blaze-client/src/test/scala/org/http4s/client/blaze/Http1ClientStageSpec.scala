package org.http4s
package client.blaze

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException

import org.http4s.blaze.{SlowTestHead, SeqTestHead}
import org.http4s.blaze.pipeline.LeafBuilder
import org.specs2.mutable.Specification

import java.nio.ByteBuffer

import org.specs2.time.NoTimeConversions
import scodec.bits.ByteVector

import scala.concurrent.Await
import scala.concurrent.duration._

import scalaz.\/-

// TODO: this needs more tests
class Http1ClientStageSpec extends Specification with NoTimeConversions {

  // Common throw away response
  val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"

  def mkBuffer(s: String): ByteBuffer =
    ByteBuffer.wrap(s.getBytes(StandardCharsets.US_ASCII))

  def getSubmission(req: Request, resp: String, timeout: Duration): String = {
    val tail = new Http1ClientStage(timeout)
    val h = new SeqTestHead(List(mkBuffer(resp)))
    LeafBuilder(tail).base(h)

    val result = tail.runRequest(req).run
    h.stageShutdown()
    val buff = Await.result(h.result, timeout + 10.seconds)
    new String(ByteVector(buff).toArray, StandardCharsets.US_ASCII)
  }

  "Http1ClientStage requests" should {
    "Run a basic request" in {
      val \/-(parsed) = Uri.fromString("http://www.foo.com")
      val req = Request(uri = parsed)

      val response = getSubmission(req, resp, 20.seconds).split("\r\n")
      val statusline = response(0)

      statusline must_== "GET / HTTP/1.1"
    }

    "Submit a request line with a query" in {
      val uri = "/huh?foo=bar"
      val \/-(parsed) = Uri.fromString("http://www.foo.com" + uri)
      val req = Request(uri = parsed)

      val response = getSubmission(req, resp, 20.seconds).split("\r\n")
      val statusline = response(0)

      statusline must_== "GET " + uri + " HTTP/1.1"
    }
  }

  "Http1ClientStage responses" should {
    "Timeout on slow response" in {
      val \/-(parsed) = Uri.fromString("http://www.foo.com")
      val req = Request(uri = parsed)

      val tail = new Http1ClientStage(1.second)
      val h = new SlowTestHead(List(mkBuffer(resp)), 10.seconds)
      LeafBuilder(tail).base(h)

      tail.runRequest(req).run must throwA[TimeoutException]
    }

    "Timeout on slow body" in {
      val \/-(parsed) = Uri.fromString("http://www.foo.com")
      val req = Request(uri = parsed)

      val tail = new Http1ClientStage(2.second)
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SlowTestHead(Seq(f,b).map(mkBuffer), 1500.millis)
      LeafBuilder(tail).base(h)

      val result = tail.runRequest(req).flatMap { resp =>
        EntityDecoder.text.apply(resp)
      }

      result.run must throwA[TimeoutException]
    }
  }

}
