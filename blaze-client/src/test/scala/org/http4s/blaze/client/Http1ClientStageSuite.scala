/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package blaze
package client

import cats.effect._
import cats.effect.concurrent.Deferred
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.Queue
import org.http4s.BuildInfo
import org.http4s.blaze.client.bits.DefaultUserAgent
import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blazecore.QueueTestHead
import org.http4s.blazecore.SeqTestHead
import org.http4s.blazecore.TestHead
import org.http4s.client.RequestKey
import org.http4s.headers.`User-Agent`
import org.http4s.syntax.all._

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.concurrent.duration._

class Http1ClientStageSuite extends Http4sSuite {
  val trampoline = org.http4s.blaze.util.Execution.trampoline

  val www_foo_test = uri"http://www.foo.test"
  val FooRequest = Request[IO](uri = www_foo_test)
  val FooRequestKey = RequestKey.fromRequest(FooRequest)

  val LongDuration = 30.seconds

  // Common throw away response
  val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"

  private val fooConnection =
    ResourceFixture[Http1Connection[IO]](mkConnection(FooRequestKey))

  private def mkConnection(
      key: RequestKey,
      userAgent: Option[`User-Agent`] = None,
  ): Resource[IO, Http1Connection[IO]] =
    Resource.make(
      IO(
        new Http1Connection[IO](
          key,
          executionContext = trampoline,
          maxResponseLineSize = 4096,
          maxHeaderLength = 40960,
          maxChunkSize = Int.MaxValue,
          chunkBufferMaxSize = 1024,
          parserMode = ParserMode.Strict,
          userAgent = userAgent,
          idleTimeoutStage = None,
        )
      )
    )(c => IO(c.shutdown()))

  private def mkBuffer(s: String): ByteBuffer =
    ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))

  private def bracketResponse[T](req: Request[IO], resp: String): Resource[IO, Response[IO]] =
    for {
      stage <- mkConnection(FooRequestKey)
      head = new SeqTestHead(resp.toSeq.map { chr =>
        val b = ByteBuffer.allocate(1)
        b.put(chr.toByte).flip()
        b
      })
      _ <- Resource.eval(IO(LeafBuilder(stage).base(head)))
      resp <- Resource.suspend(stage.runRequest(req, IO.never))
    } yield resp

  private def getSubmission(
      req: Request[IO],
      resp: String,
      stage: Http1Connection[IO],
  ): IO[(String, String)] =
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
      req0 = req.withBodyStream(req.body.onFinalizeWeak(d.complete(())))
      response <- stage.runRequest(req0, IO.never)
      result <- response.use(_.as[String])
      _ <- IO(h.stageShutdown())
      buff <- IO.fromFuture(IO(h.result))
      _ <- d.get
      request = new String(buff.array(), StandardCharsets.ISO_8859_1)
    } yield (request, result)

  private def getSubmission(
      req: Request[IO],
      resp: String,
      userAgent: Option[`User-Agent`] = None,
  ): IO[(String, String)] = {
    val key = RequestKey.fromRequest(req)
    mkConnection(key, userAgent).use(tail => getSubmission(req, resp, tail))
  }

  test("Run a basic request") {
    getSubmission(FooRequest, resp).map { case (request, response) =>
      val statusLine = request.split("\r\n").apply(0)
      assertEquals(statusLine, "GET / HTTP/1.1")
      assertEquals(response, "done")
    }
  }

  test("Submit a request line with a query") {
    val uri = "/huh?foo=bar"
    val Right(parsed) = Uri.fromString("http://www.foo.test" + uri)
    val req = Request[IO](uri = parsed)

    getSubmission(req, resp).map { case (request, response) =>
      val statusLine = request.split("\r\n").apply(0)
      assertEquals(statusLine, "GET " + uri + " HTTP/1.1")
      assertEquals(response, "done")
    }
  }

  fooConnection.test("Fail when attempting to get a second request with one in progress") { tail =>
    val (frag1, frag2) = resp.splitAt(resp.length - 1)

    val h = new SeqTestHead(List(mkBuffer(frag1), mkBuffer(frag2), mkBuffer(resp)))
    LeafBuilder(tail).base(h)

    (for {
      _ <- tail.runRequest(FooRequest, IO.never) // we remain in the body
      _ <- tail.runRequest(FooRequest, IO.never)
    } yield ()).intercept[Http1Connection.InProgressException.type]
  }

  fooConnection.test("Alert the user if the body is to short") { tail =>
    val resp = "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\ndone"

    val h = new SeqTestHead(List(mkBuffer(resp)))
    LeafBuilder(tail).base(h)

    Resource
      .suspend(tail.runRequest(FooRequest, IO.never))
      .use(_.body.compile.drain)
      .intercept[InvalidBodyException]
  }

  test("Interpret a lack of length with a EOF as a valid message") {
    val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

    getSubmission(FooRequest, resp).map(_._2).assertEquals("done")
  }

  test("Utilize a provided Host header") {
    val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

    val req = FooRequest.withHeaders(headers.Host("bar.test"))

    getSubmission(req, resp).map { case (request, response) =>
      val requestLines = request.split("\r\n").toList
      assert(requestLines.contains("Host: bar.test"))
      assertEquals(response, "done")
    }
  }

  test("Insert a User-Agent header") {
    val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

    getSubmission(FooRequest, resp, DefaultUserAgent).map { case (request, response) =>
      val requestLines = request.split("\r\n").toList
      assert(requestLines.contains(s"User-Agent: http4s-blaze/${BuildInfo.version}"))
      assertEquals(response, "done")
    }
  }

  test("Use User-Agent header provided in Request") {
    val resp = "HTTP/1.1 200 OK\r\n\r\ndone"
    val req = FooRequest.withHeaders(`User-Agent`(ProductId("myagent")))

    getSubmission(req, resp).map { case (request, response) =>
      val requestLines = request.split("\r\n").toList
      assert(requestLines.contains("User-Agent: myagent"))
      assertEquals(response, "done")
    }
  }

  fooConnection.test("Not add a User-Agent header when configured with None") { tail =>
    val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

    getSubmission(FooRequest, resp, tail).map { case (request, response) =>
      val requestLines = request.split("\r\n").toList
      assertEquals(requestLines.find(_.startsWith("User-Agent")), None)
      assertEquals(response, "done")
    }
  }

  // TODO fs2 port - Currently is elevating the http version to 1.1 causing this test to fail
  test("Allow an HTTP/1.0 request without a Host header".ignore) {
    val resp = "HTTP/1.0 200 OK\r\n\r\ndone"

    val req = Request[IO](uri = www_foo_test, httpVersion = HttpVersion.`HTTP/1.0`)

    getSubmission(req, resp).map { case (request, response) =>
      assert(!request.contains("Host:"))
      assertEquals(response, "done")
    }
  }

  test("Support flushing the prelude") {
    val req = Request[IO](uri = www_foo_test, httpVersion = HttpVersion.`HTTP/1.0`)
    /*
     * We flush the prelude first to test connection liveness in pooled
     * scenarios before we consume the body.  Make sure we can handle
     * it.  Ensure that we still get a well-formed response.
     */
    getSubmission(req, resp).map(_._2).assertEquals("done")
  }

  fooConnection.test("Not expect body if request was a HEAD request") { tail =>
    val contentLength = 12345L
    val resp = s"HTTP/1.1 200 OK\r\nContent-Length: $contentLength\r\n\r\n"
    val headRequest = FooRequest.withMethod(Method.HEAD)

    val h = new SeqTestHead(List(mkBuffer(resp)))
    LeafBuilder(tail).base(h)

    Resource.suspend(tail.runRequest(headRequest, IO.never)).use { response =>
      assertEquals(response.contentLength, Some(contentLength))

      // body is empty due to it being HEAD request
      response.body.compile.toVector.map(_.foldLeft(0L)((long, _) => long + 1L)).assertEquals(0L)
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

    test("Support trailer headers") {
      val hs: IO[Headers] = bracketResponse(req, resp).use { (response: Response[IO]) =>
        for {
          _ <- response.as[String]
          hs <- response.trailerHeaders
        } yield hs
      }

      hs.map(_.headers.mkString).assertEquals("Foo: Bar")
    }

    test("Fail to get trailers before they are complete") {
      val hs: IO[Headers] = bracketResponse(req, resp).use { (response: Response[IO]) =>
        for {
          hs <- response.trailerHeaders
        } yield hs
      }

      hs.intercept[IllegalStateException]
    }
  }

  fooConnection.test("Close idle connection after server closes it") { tail =>
    val h = new TestHead("EofingTestHead") {
      private val bodyIt = Seq(mkBuffer(resp)).iterator

      override def readRequest(size: Int): Future[ByteBuffer] =
        synchronized {
          if (!closed && bodyIt.hasNext) Future.successful(bodyIt.next())
          else Future.failed(EOF)
        }
    }
    LeafBuilder(tail).base(h)

    for {
      _ <- tail.runRequest(FooRequest, IO.never) // the first request succeeds
      _ <- IO.sleep(200.millis) // then the server closes the connection
      isClosed <- IO(
        tail.isClosed
      ) // and the client should recognize that the connection has been closed
    } yield assert(isClosed)
  }
}
