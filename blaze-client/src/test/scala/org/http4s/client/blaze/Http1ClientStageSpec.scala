package org.http4s
package client
package blaze

import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer

import org.http4s.blaze.SeqTestHead
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.util.CaseInsensitiveString._

import bits.DefaultUserAgent

import org.specs2.mutable.Specification
import scodec.bits.ByteVector

import scala.concurrent.Await
import scala.concurrent.duration._

import scalaz.\/-

// TODO: this needs more tests
class Http1ClientStageSpec extends Specification {

  val ec = org.http4s.blaze.util.Execution.trampoline

  val www_foo_test = Uri.uri("http://www.foo.test")
  val FooRequest = Request(uri = www_foo_test)
  val FooRequestKey = RequestKey.fromRequest(FooRequest)

  val LongDuration = 30.seconds

  // Common throw away response
  val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"

  def mkBuffer(s: String): ByteBuffer =
    ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))

  def getSubmission(req: Request, resp: String, stage: Http1Connection, flushPrelude: Boolean): (String, String) = {
    val h = new SeqTestHead(resp.toSeq.map{ chr =>
      val b = ByteBuffer.allocate(1)
      b.put(chr.toByte).flip()
      b
    })
    LeafBuilder(stage).base(h)

    val result = new String(stage.runRequest(req, flushPrelude)
      .run
      .body
      .runLog
      .run
      .foldLeft(ByteVector.empty)(_ ++ _)
      .toArray)

    h.stageShutdown()
    val buff = Await.result(h.result, 10.seconds)
    val request = new String(ByteVector(buff).toArray, StandardCharsets.ISO_8859_1)
    (request, result)
  }

  def getSubmission(req: Request, resp: String, flushPrelude: Boolean = false): (String, String) = {
    val key = RequestKey.fromRequest(req)
    val tail = new Http1Connection(key, DefaultUserAgent, ec)
    try getSubmission(req, resp, tail, flushPrelude)
    finally { tail.shutdown() }
  }

  "Http1ClientStage" should {

    "Run a basic request" in {
      val (request, response) = getSubmission(FooRequest, resp)
      val statusline = request.split("\r\n").apply(0)

      statusline must_== "GET / HTTP/1.1"
      response must_== "done"
    }

    "Submit a request line with a query" in {
      val uri = "/huh?foo=bar"
      val \/-(parsed) = Uri.fromString("http://www.foo.test" + uri)
      val req = Request(uri = parsed)

      val (request, response) = getSubmission(req, resp)
      val statusline = request.split("\r\n").apply(0)

      statusline must_== "GET " + uri + " HTTP/1.1"
      response must_== "done"
    }

    "Fail when attempting to get a second request with one in progress" in {
      val tail = new Http1Connection(FooRequestKey, DefaultUserAgent, ec)
      val (frag1,frag2) = resp.splitAt(resp.length-1)
      val h = new SeqTestHead(List(mkBuffer(frag1), mkBuffer(frag2), mkBuffer(resp)))
      LeafBuilder(tail).base(h)

      try {
        tail.runRequest(FooRequest, false).run  // we remain in the body
        tail.runRequest(FooRequest, false).run must throwA[Http1Connection.InProgressException.type]
      }
      finally {
        tail.shutdown()
      }
    }

    "Reset correctly" in {
      val tail = new Http1Connection(FooRequestKey, DefaultUserAgent, ec)
      try {
        val h = new SeqTestHead(List(mkBuffer(resp), mkBuffer(resp)))
        LeafBuilder(tail).base(h)

        // execute the first request and run the body to reset the stage
        tail.runRequest(FooRequest, false).run.body.run.run

        val result = tail.runRequest(FooRequest, false).run
        tail.shutdown()

        result.headers.size must_== 1
      }
      finally {
        tail.shutdown()
      }
    }

    "Alert the user if the body is to short" in {
      val resp = "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\ndone"
      val tail = new Http1Connection(FooRequestKey, DefaultUserAgent, ec)

      try {
        val h = new SeqTestHead(List(mkBuffer(resp)))
        LeafBuilder(tail).base(h)

        val result = tail.runRequest(FooRequest, false).run

        result.body.run.run must throwA[InvalidBodyException]
      }
      finally {
        tail.shutdown()
      }
    }

    "Interpret a lack of length with a EOF as a valid message" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val (_, response) = getSubmission(FooRequest, resp)

      response must_==("done")
    }

    "Utilize a provided Host header" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val req = FooRequest.replaceAllHeaders(headers.Host("bar.test"))

      val (request, response) = getSubmission(req, resp)

      val requestLines = request.split("\r\n").toList

      requestLines must contain("Host: bar.test")
      response must_==("done")
    }

    "Insert a User-Agent header" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val (request, response) = getSubmission(FooRequest, resp)

      val requestLines = request.split("\r\n").toList

      requestLines must contain(DefaultUserAgent.get.toString)
      response must_==("done")
    }

    "Use User-Agent header provided in Request" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val req = FooRequest.replaceAllHeaders(Header.Raw("User-Agent".ci, "myagent"))

      val (request, response) = getSubmission(req, resp)

      val requestLines = request.split("\r\n").toList

      requestLines must contain("User-Agent: myagent")
      response must_==("done")
    }

    "Not add a User-Agent header when configured with None" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"
      val tail = new Http1Connection(FooRequestKey, None, ec)

      try {
        val (request, response) = getSubmission(FooRequest, resp, tail, false)
        tail.shutdown()

        val requestLines = request.split("\r\n").toList

        requestLines.find(_.startsWith("User-Agent")) must beNone
        response must_==("done")
      }
      finally {
        tail.shutdown()
      }
    }

    "Allow an HTTP/1.0 request without a Host header" in {
      val resp = "HTTP/1.0 200 OK\r\n\r\ndone"

      val req = Request(uri = www_foo_test, httpVersion = HttpVersion.`HTTP/1.0`)

      val (request, response) = getSubmission(req, resp)

      request must not contain("Host:")
      response must_==("done")
    }

    "Support flushing the prelude" in {
      val req = Request(uri = www_foo_test, httpVersion = HttpVersion.`HTTP/1.0`)
      /*
       * We flush the prelude first to test connection liveness in pooled
       * scenarios before we consume the body.  Make sure we can handle
       * it.  Ensure that we still get a well-formed response.
       */
      val (request, response) = getSubmission(req, resp, true)
      response must_==("done")
    }
  }
}

