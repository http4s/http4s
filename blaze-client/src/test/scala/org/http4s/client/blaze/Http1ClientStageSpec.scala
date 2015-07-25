package org.http4s
package client
package blaze

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException
import java.nio.ByteBuffer

import org.http4s.blaze.{SlowTestHead, SeqTestHead}
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.util.CaseInsensitiveString._

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import scodec.bits.ByteVector

import scala.concurrent.Await
import scala.concurrent.duration._

import scalaz.\/-
import scalaz.concurrent.Strategy._
import scalaz.concurrent.Task
import scalaz.stream.{time, Process}

// TODO: this needs more tests
class Http1ClientStageSpec extends Specification with NoTimeConversions {

  val www_foo_com = Uri.uri("http://www.foo.com")
  val FooRequest = Request(uri = www_foo_com)
  
  val LongDuration = 30.seconds

  // Common throw away response
  val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"

  def mkBuffer(s: String): ByteBuffer =
    ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))

  def getSubmission(req: Request, resp: String, timeout: Duration, stage: Http1ClientStage): (String, String) = {
    //    val h = new SeqTestHead(List(mkBuffer(resp)))
    val h = new SeqTestHead(resp.toSeq.map{ chr =>
      val b = ByteBuffer.allocate(1)
      b.put(chr.toByte).flip()
      b
    })
    LeafBuilder(stage).base(h)

    val result = new String(stage.runRequest(req)
      .run
      .body
      .runLog
      .run
      .foldLeft(ByteVector.empty)(_ ++ _)
      .toArray)

    h.stageShutdown()
    val buff = Await.result(h.result, timeout + 10.seconds)
    val request = new String(ByteVector(buff).toArray, StandardCharsets.ISO_8859_1)
    (request, result)
  }

  def getSubmission(req: Request, resp: String, timeout: Duration): (String, String) =
    getSubmission(req, resp, timeout, new Http1ClientStage(DefaultUserAgent, timeout))


  "Http1ClientStage" should {

    "Run a basic request" in {
      val (request, response) = getSubmission(FooRequest, resp, LongDuration)
      val statusline = request.split("\r\n").apply(0)

      statusline must_== "GET / HTTP/1.1"
      response must_== "done"
    }

    "Submit a request line with a query" in {
      val uri = "/huh?foo=bar"
      val \/-(parsed) = Uri.fromString("http://www.foo.com" + uri)
      val req = Request(uri = parsed)

      val (request, response) = getSubmission(req, resp, LongDuration)
      val statusline = request.split("\r\n").apply(0)

      statusline must_== "GET " + uri + " HTTP/1.1"
      response must_== "done"
    }

    "Fail when attempting to get a second request with one in progress" in {
      val tail = new Http1ClientStage(DefaultUserAgent, LongDuration)
      val h = new SeqTestHead(List(mkBuffer(resp), mkBuffer(resp)))
      LeafBuilder(tail).base(h)

      tail.runRequest(FooRequest).run  // we remain in the body

      tail.runRequest(FooRequest).run must throwA[Http1ClientStage.InProgressException.type]
    }

    "Reset correctly" in {
      val tail = new Http1ClientStage(DefaultUserAgent, LongDuration)
      val h = new SeqTestHead(List(mkBuffer(resp), mkBuffer(resp)))
      LeafBuilder(tail).base(h)

      // execute the first request and run the body to reset the stage
      tail.runRequest(FooRequest).run.body.run.run

      val result = tail.runRequest(FooRequest).run
      result.headers.size must_== 1
    }

    "Alert the user if the body is to short" in {
      val resp = "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\ndone"

      val tail = new Http1ClientStage(DefaultUserAgent, LongDuration)
      val h = new SeqTestHead(List(mkBuffer(resp)))
      LeafBuilder(tail).base(h)

      val result = tail.runRequest(FooRequest).run

      result.body.run.run must throwA[InvalidBodyException]
    }

    "Interpret a lack of length with a EOF as a valid message" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val (_, response) = getSubmission(FooRequest, resp, LongDuration)

      response must_==("done")
    }

    "Utilize a provided Host header" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val req = FooRequest.withHeaders(headers.Host("bar.com"))

      val (request, response) = getSubmission(req, resp, LongDuration)

      val requestLines = request.split("\r\n").toList

      requestLines must contain("Host: bar.com")
      response must_==("done")
    }

    "Insert a User-Agent header" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val (request, response) = getSubmission(FooRequest, resp, LongDuration)

      val requestLines = request.split("\r\n").toList

      requestLines must contain(DefaultUserAgent.get.toString)
      response must_==("done")
    }

    "Use User-Agent header provided in Request" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val req = FooRequest.withHeaders(Header.Raw("User-Agent".ci, "myagent"))

      val (request, response) = getSubmission(req, resp, LongDuration)

      val requestLines = request.split("\r\n").toList

      requestLines must contain("User-Agent: myagent")
      response must_==("done")
    }

    "Not add a User-Agent header when configured with None" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val tail = new Http1ClientStage(None, LongDuration)
      val (request, response) = getSubmission(FooRequest, resp, LongDuration, tail)

      val requestLines = request.split("\r\n").toList

      requestLines.find(_.startsWith("User-Agent")) must beNone
      response must_==("done")
    }

    "Allow an HTTP/1.0 request without a Host header" in {
      val resp = "HTTP/1.0 200 OK\r\n\r\ndone"

      val req = Request(uri = www_foo_com, httpVersion = HttpVersion.`HTTP/1.0`)

      val (request, response) = getSubmission(req, resp, 20.seconds)

      request must not contain("Host:")
      response must_==("done")
    }
  }

  "Http1ClientStage responses" should {
    "Timeout immediately with a timeout of 0 seconds" in {
      val tail = new Http1ClientStage(DefaultUserAgent, 0.seconds)
      val h = new SlowTestHead(List(mkBuffer(resp)), 0.milli)
      LeafBuilder(tail).base(h)

      tail.runRequest(FooRequest).run must throwA[TimeoutException]
    }

    "Timeout on slow response" in {
      val tail = new Http1ClientStage(DefaultUserAgent, 1.second)
      val h = new SlowTestHead(List(mkBuffer(resp)), 10.seconds)
      LeafBuilder(tail).base(h)

      tail.runRequest(FooRequest).run must throwA[TimeoutException]
    }

    "Timeout on slow POST body" in {


      def dataStream(n: Int): Process[Task, ByteVector] = {
        implicit def defaultSecheduler = DefaultTimeoutScheduler
        val interval = 1000.millis
        time.awakeEvery(interval)
          .map(_ => ByteVector.empty)
          .take(n)
      }

      val req = Request(method = Method.POST, uri = www_foo_com, body = dataStream(4))

      val tail = new Http1ClientStage(DefaultUserAgent, 1.second)
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SeqTestHead(Seq(f,b).map(mkBuffer))
      LeafBuilder(tail).base(h)

      val result = tail.runRequest(req).flatMap { resp =>
        EntityDecoder.text.decode(resp).run
      }

      result.run must throwA[TimeoutException]
    }

    "Timeout on slow response body" in {
      val tail = new Http1ClientStage(DefaultUserAgent, 2.second)
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SlowTestHead(Seq(f,b).map(mkBuffer), 1500.millis)
      LeafBuilder(tail).base(h)

      val result = tail.runRequest(FooRequest).flatMap { resp =>
        EntityDecoder.text.decode(resp).run
      }

      result.run must throwA[TimeoutException]
    }
  }
}
