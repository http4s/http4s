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
import scalaz.concurrent.{Strategy, Task}

// TODO: this needs more tests
class Http1ClientStageSpec extends Specification {

  val ec = org.http4s.blaze.util.Execution.trampoline
  val es = Strategy.DefaultExecutorService

  val www_foo_test = Uri.uri("http://www.foo.test")
  val FooRequest = Request(uri = www_foo_test)
  val FooRequestKey = RequestKey.fromRequest(FooRequest)

  val LongDuration = 30.seconds

  // Common throw away response
  val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"

  // The executor in here needs to be shut down manually because the `BlazeClient` class won't do it for us
  private val defaultConfig = BlazeClientConfig.defaultConfig

  private def mkConnection(key: RequestKey) = new Http1Connection(key, defaultConfig, es, ec)

  private def mkBuffer(s: String): ByteBuffer = ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))

  private def bracketResponse[T](req: Request, resp: String, flushPrelude: Boolean)(f: Response => Task[T]): Task[T] = {
    val stage = new Http1Connection(FooRequestKey, defaultConfig.copy(userAgent = None), es, ec)
    Task.suspend {
      val h = new SeqTestHead(resp.toSeq.map{ chr =>
        val b = ByteBuffer.allocate(1)
        b.put(chr.toByte).flip()
        b
      })
      LeafBuilder(stage).base(h)

      for {
        resp <- stage.runRequest(req, flushPrelude)
        t    <- f(resp)
        _    <- Task { stage.shutdown() }
      } yield t
    }

  }

  private def getSubmission(req: Request, resp: String, stage: Http1Connection, flushPrelude: Boolean): (String, String) = {
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

  private def getSubmission(req: Request, resp: String, flushPrelude: Boolean = false): (String, String) = {
    val key = RequestKey.fromRequest(req)
    val tail = mkConnection(key)
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
      val tail = mkConnection(FooRequestKey)
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
      val tail = mkConnection(FooRequestKey)
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
      val tail = mkConnection(FooRequestKey)

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
      val tail = new Http1Connection(FooRequestKey, defaultConfig.copy(userAgent = None), es, ec)

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

    "Not expect body if request was a HEAD request" in {
      val contentLength = 12345L
      val resp = s"HTTP/1.1 200 OK\r\nContent-Length: $contentLength\r\n\r\n"
      val headRequest = FooRequest.copy(method = Method.HEAD)
      val tail = mkConnection(FooRequestKey)
      try {
        val h = new SeqTestHead(List(mkBuffer(resp)))
        LeafBuilder(tail).base(h)

        val response = tail.runRequest(headRequest, false).run
        response.contentLength must_== Some(contentLength)

        // connection reusable immediately after headers read
        tail.isRecyclable must_=== true

        // body is empty due to it being HEAD request
        response.body.runLog.run.foldLeft(0L)(_ + _.length) must_== 0L
      } finally {
        tail.shutdown()
      }
    }

    {
      val resp = "HTTP/1.1 200 OK\r\n" +
        "Transfer-Encoding: chunked\r\n\r\n" +
        "3\r\n" +
        "foo\r\n" +
        "0\r\n" +
        "Foo:Bar\r\n" +
        "\r\n"

      val req = Request(uri = www_foo_test, httpVersion = HttpVersion.`HTTP/1.1`)

      "Support trailer headers" in {
        val hs: Task[Headers] = bracketResponse(req, resp, false){ response: Response =>
          for {
            body  <- response.as[String]
            hs <- response.trailerHeaders
          } yield hs
        }

        hs.run.mkString must_== "Foo: Bar"
      }

      "Fail to get trailers before they are complete" in {
        val hs: Task[Headers] = bracketResponse(req, resp, false){ response: Response =>
          for {
            //body  <- response.as[String]
            hs <- response.trailerHeaders
          } yield hs
        }

        hs.run must throwA[IllegalStateException]
      }
    }
  }
}

