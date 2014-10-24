package org.http4s
package client.blaze

import java.nio.charset.StandardCharsets

import org.http4s.blaze.SeqTestHead
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

  def getSubmission(req: Request, resp: String): String = {
    val tail = new Http1ClientStage()
    val h = new SeqTestHead(List(ByteBuffer.wrap(resp.getBytes(StandardCharsets.US_ASCII))))
    LeafBuilder(tail).base(h)

    val result = tail.runRequest(req).run
    h.stageShutdown()
    val buff = Await.result(h.result, 30.seconds)
    new String(ByteVector(buff).toArray, StandardCharsets.US_ASCII)
  }

  "Http1ClientStage" should {
    "Submit a request line with a query" in {
      val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"
      val uri = "http://www.foo.com/huh?foo=bar"
      val \/-(parsed) = Uri.fromString(uri)
      val req = Request(uri = parsed)

      val response = getSubmission(req, resp).split("\r\n")
      val statusline = response(0)

      statusline must_== "GET " + uri + " HTTP/1.1"
    }
  }

}
