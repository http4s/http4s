package org.http4s
package client
package blaze

import cats.effect._
import cats.effect.concurrent.Deferred
import cats.implicits._
import fs2.Stream
import fs2.concurrent.Queue
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blazecore.{QueueTestHead, SeqTestHead}
import org.http4s.client.blaze.bits.DefaultUserAgent
import org.http4s.headers.`User-Agent`
import scala.concurrent.duration._

class Http1ClientStageSpec extends Http4sSpec {

  val trampoline = org.http4s.blaze.util.Execution.trampoline

  val www_foo_test = Uri.uri("http://www.foo.test")
  val FooRequest = Request[IO](uri = www_foo_test)
  val FooRequestKey = RequestKey.fromRequest(FooRequest)

  val LongDuration = 30.seconds

  // Common throw away response
  val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"

  private def mkConnection(key: RequestKey, userAgent: Option[`User-Agent`] = None) =
    new Http1Connection[IO](
      key,
      executionContext = trampoline,
      maxResponseLineSize = 4096,
      maxHeaderLength = 40960,
      maxChunkSize = Int.MaxValue,
      chunkBufferMaxSize = 1024,
      parserMode = ParserMode.Strict,
      userAgent = userAgent
    )

  private def mkBuffer(s: String): ByteBuffer =
    ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))

  private def bracketResponse[T](req: Request[IO], resp: String)(
      f: Response[IO] => IO[T]): IO[T] = {
    val stage = mkConnection(FooRequestKey)
    IO.suspend {
      val h = new SeqTestHead(resp.toSeq.map { chr =>
        val b = ByteBuffer.allocate(1)
        b.put(chr.toByte).flip()
        b
      })
      LeafBuilder(stage).base(h)

      for {
        resp <- stage.runRequest(req, IO.never)
        t <- f(resp)
        _ <- IO(stage.shutdown())
      } yield t
    }

  }

  private def getSubmission(
      req: Request[IO],
      resp: String,
      stage: Http1Connection[IO]): IO[(String, String)] =
    for {
      q <- Queue.unbounded[IO, Option[ByteBuffer]]
      h = new QueueTestHead(q)
      d <- Deferred[IO, Unit]
      _ <- IO(LeafBuilder(stage).base(h))
      _ <- (d.get >> Stream
        .emits(resp.toList)
        .map { c =>
          val b = ByteBuffer.allocate(1)
          b.put(c.toByte).flip()
          b
        }
        .noneTerminate
        .through(q.enqueue)
        .compile
        .drain).start
      req0 = req.withBodyStream(req.body.onFinalize(d.complete(())))
      response <- stage.runRequest(req0, IO.never)
      result <- response.as[String]
      _ <- IO(h.stageShutdown())
      buff <- IO.fromFuture(IO(h.result))
      request = new String(buff.array(), StandardCharsets.ISO_8859_1)
    } yield (request, result)

  private def getSubmission(
      req: Request[IO],
      resp: String,
      userAgent: Option[`User-Agent`] = None): IO[(String, String)] = {
    val key = RequestKey.fromRequest(req)
    val tail = mkConnection(key, userAgent)
    getSubmission(req, resp, tail)
  }

  "Http1ClientStage" should {

    "Run a basic request" in {
      val (request, response) = getSubmission(FooRequest, resp).unsafeRunSync()
      val statusline = request.split("\r\n").apply(0)
      statusline must_== "GET / HTTP/1.1"
      response must_== "done"
    }

    "Submit a request line with a query" in {
      val uri = "/huh?foo=bar"
      val Right(parsed) = Uri.fromString("http://www.foo.test" + uri)
      val req = Request[IO](uri = parsed)

      val (request, response) = getSubmission(req, resp).unsafeRunSync()
      val statusline = request.split("\r\n").apply(0)

      statusline must_== "GET " + uri + " HTTP/1.1"
      response must_== "done"
    }

    "Fail when attempting to get a second request with one in progress" in {
      val tail = mkConnection(FooRequestKey)
      val (frag1, frag2) = resp.splitAt(resp.length - 1)
      val h = new SeqTestHead(List(mkBuffer(frag1), mkBuffer(frag2), mkBuffer(resp)))
      LeafBuilder(tail).base(h)

      try {
        tail.runRequest(FooRequest, IO.never).unsafeRunAsync {
          case Right(_) => (); case Left(_) => ()
        } // we remain in the body
        tail
          .runRequest(FooRequest, IO.never)
          .unsafeRunSync() must throwA[Http1Connection.InProgressException.type]
      } finally {
        tail.shutdown()
      }
    }

    "Reset correctly" in {
      val tail = mkConnection(FooRequestKey)
      try {
        val h = new SeqTestHead(List(mkBuffer(resp), mkBuffer(resp)))
        LeafBuilder(tail).base(h)

        // execute the first request and run the body to reset the stage
        tail.runRequest(FooRequest, IO.never).unsafeRunSync().body.compile.drain.unsafeRunSync()

        val result = tail.runRequest(FooRequest, IO.never).unsafeRunSync()
        tail.shutdown()

        result.headers.size must_== 1
      } finally {
        tail.shutdown()
      }
    }

    "Alert the user if the body is to short" in {
      val resp = "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\ndone"
      val tail = mkConnection(FooRequestKey)

      try {
        val h = new SeqTestHead(List(mkBuffer(resp)))
        LeafBuilder(tail).base(h)

        val result = tail.runRequest(FooRequest, IO.never).unsafeRunSync()

        result.body.compile.drain.unsafeRunSync() must throwA[InvalidBodyException]
      } finally {
        tail.shutdown()
      }
    }

    "Interpret a lack of length with a EOF as a valid message" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val (_, response) = getSubmission(FooRequest, resp).unsafeRunSync()

      response must_== "done"
    }

    "Utilize a provided Host header" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val req = FooRequest.withHeaders(headers.Host("bar.test"))

      val (request, response) = getSubmission(req, resp).unsafeRunSync()

      val requestLines = request.split("\r\n").toList

      requestLines must contain("Host: bar.test")
      response must_== "done"
    }

    "Insert a User-Agent header" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val (request, response) = getSubmission(FooRequest, resp, DefaultUserAgent).unsafeRunSync()

      val requestLines = request.split("\r\n").toList

      requestLines must contain(s"User-Agent: http4s-blaze/${BuildInfo.version}")
      response must_== "done"
    }

    "Use User-Agent header provided in Request" in skipOnCi {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

      val req = FooRequest.withHeaders(Header.Raw("User-Agent".ci, "myagent"))

      val (request, response) = getSubmission(req, resp).unsafeRunSync()

      val requestLines = request.split("\r\n").toList

      requestLines must contain("User-Agent: myagent")
      response must_== "done"
    }

    "Not add a User-Agent header when configured with None" in {
      val resp = "HTTP/1.1 200 OK\r\n\r\ndone"
      val tail = mkConnection(FooRequestKey)

      try {
        val (request, response) = getSubmission(FooRequest, resp, tail).unsafeRunSync()
        tail.shutdown()

        val requestLines = request.split("\r\n").toList

        requestLines.find(_.startsWith("User-Agent")) must beNone
        response must_== "done"
      } finally {
        tail.shutdown()
      }
    }

    // TODO fs2 port - Currently is elevating the http version to 1.1 causing this test to fail
    "Allow an HTTP/1.0 request without a Host header" in skipOnCi {
      val resp = "HTTP/1.0 200 OK\r\n\r\ndone"

      val req = Request[IO](uri = www_foo_test, httpVersion = HttpVersion.`HTTP/1.0`)

      val (request, response) = getSubmission(req, resp).unsafeRunSync()

      request must not contain "Host:"
      response must_== "done"
    }.pendingUntilFixed

    "Support flushing the prelude" in {
      val req = Request[IO](uri = www_foo_test, httpVersion = HttpVersion.`HTTP/1.0`)
      /*
       * We flush the prelude first to test connection liveness in pooled
       * scenarios before we consume the body.  Make sure we can handle
       * it.  Ensure that we still get a well-formed response.
       */
      val (_, response) = getSubmission(req, resp).unsafeRunSync()
      response must_== "done"
    }

    "Not expect body if request was a HEAD request" in {
      val contentLength = 12345L
      val resp = s"HTTP/1.1 200 OK\r\nContent-Length: $contentLength\r\n\r\n"
      val headRequest = FooRequest.withMethod(Method.HEAD)
      val tail = mkConnection(FooRequestKey)
      try {
        val h = new SeqTestHead(List(mkBuffer(resp)))
        LeafBuilder(tail).base(h)

        val response = tail.runRequest(headRequest, IO.never).unsafeRunSync()
        response.contentLength must beSome(contentLength)

        // connection reusable immediately after headers read
        tail.isRecyclable must_=== true

        // body is empty due to it being HEAD request
        response.body.compile.toVector
          .unsafeRunSync()
          .foldLeft(0L)((long, byte) => long + 1L) must_== 0L
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

      val req = Request[IO](uri = www_foo_test, httpVersion = HttpVersion.`HTTP/1.1`)

      "Support trailer headers" in {
        val hs: IO[Headers] = bracketResponse(req, resp) { response: Response[IO] =>
          for {
            _ <- response.as[String]
            hs <- response.trailerHeaders
          } yield hs
        }

        hs.unsafeRunSync().mkString must_== "Foo: Bar"
      }

      "Fail to get trailers before they are complete" in {
        val hs: IO[Headers] = bracketResponse(req, resp) { response: Response[IO] =>
          for {
            //body  <- response.as[String]
            hs <- response.trailerHeaders
          } yield hs
        }

        hs.unsafeRunSync() must throwA[IllegalStateException]
      }
    }
  }
}
