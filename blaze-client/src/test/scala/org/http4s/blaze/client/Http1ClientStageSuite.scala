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
import cats.effect.kernel.Deferred
import cats.effect.std.{Dispatcher, Queue}
import cats.syntax.all._
import fs2.Stream
import org.http4s.blaze.pipeline.Command.EOF
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.http4s.blaze.client.bits.DefaultUserAgent
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blazecore.{QueueTestHead, SeqTestHead, TestHead}
import org.http4s.BuildInfo
import org.http4s.client.RequestKey
import org.http4s.headers.`User-Agent`
import org.http4s.syntax.all._
import org.http4s.testing.DispatcherIOFixture
import scala.concurrent.Future
import scala.concurrent.duration._

class Http1ClientStageSuite extends Http4sSuite with DispatcherIOFixture {

  val trampoline = org.http4s.blaze.util.Execution.trampoline

  val www_foo_test = uri"http://www.foo.test"
  val FooRequest = Request[IO](uri = www_foo_test)
  val FooRequestKey = RequestKey.fromRequest(FooRequest)

  val LongDuration = 30.seconds

  // Common throw away response
  val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"

  private val fooConnection =
    ResourceFixture[Http1Connection[IO]] {
      for {
        dispatcher <- Dispatcher[IO]
        connection <- Resource[IO, Http1Connection[IO]] {
          IO {
            val connection = mkConnection(FooRequestKey, dispatcher)
            (connection, IO.delay(connection.shutdown()))
          }
        }
      } yield connection
    }

  private def mkConnection(
      key: RequestKey,
      dispatcher: Dispatcher[IO],
      userAgent: Option[`User-Agent`] = None) =
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
      dispatcher = dispatcher
    )

  private def mkBuffer(s: String): ByteBuffer =
    ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))

  private def bracketResponse[T](
      req: Request[IO],
      resp: String,
      dispatcher: Dispatcher[IO]): Resource[IO, Response[IO]] = {
    val stageResource = Resource(IO {
      val stage = mkConnection(FooRequestKey, dispatcher)
      val h = new SeqTestHead(resp.toSeq.map { chr =>
        val b = ByteBuffer.allocate(1)
        b.put(chr.toByte).flip()
        b
      })
      LeafBuilder(stage).base(h)
      (stage, IO(stage.shutdown()))
    })

    for {
      stage <- stageResource
      resp <- Resource.suspend(stage.runRequest(req))
    } yield resp
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
        .evalMap(q.offer)
        .compile
        .drain).start
      req0 = req.withBodyStream(req.body.onFinalizeWeak(d.complete(()).void))
      response <- stage.runRequest(req0)
      result <- response.use(_.as[String])
      _ <- IO(h.stageShutdown())
      buff <- IO.fromFuture(IO(h.result))
      _ <- d.get
      request = new String(buff.array(), StandardCharsets.ISO_8859_1)
    } yield (request, result)

  private def getSubmission(
      req: Request[IO],
      resp: String,
      dispatcher: Dispatcher[IO],
      userAgent: Option[`User-Agent`] = None): IO[(String, String)] = {
    val key = RequestKey.fromRequest(req)
    val tail = mkConnection(key, dispatcher, userAgent)
    getSubmission(req, resp, tail)
  }

  dispatcher.test("Run a basic request".flaky) { dispatcher =>
    getSubmission(FooRequest, resp, dispatcher).map { case (request, response) =>
      val statusLine = request.split("\r\n").apply(0)
      assertEquals(statusLine, "GET / HTTP/1.1")
      assertEquals(response, "done")
    }
  }

  dispatcher.test("Submit a request line with a query".flaky) { dispatcher =>
    val uri = "/huh?foo=bar"
    val Right(parsed) = Uri.fromString("http://www.foo.test" + uri)
    val req = Request[IO](uri = parsed)

    getSubmission(req, resp, dispatcher).map { case (request, response) =>
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
      _ <- tail.runRequest(FooRequest) // we remain in the body
      _ <- tail.runRequest(FooRequest)
    } yield ()).intercept[Http1Connection.InProgressException.type]
  }

  fooConnection.test("Alert the user if the body is to short") { tail =>
    val resp = "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\ndone"

    val h = new SeqTestHead(List(mkBuffer(resp)))
    LeafBuilder(tail).base(h)

    Resource
      .suspend(tail.runRequest(FooRequest))
      .use(_.body.compile.drain)
      .intercept[InvalidBodyException]
  }

  dispatcher.test("Interpret a lack of length with a EOF as a valid message") { dispatcher =>
    val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

    getSubmission(FooRequest, resp, dispatcher).map(_._2).assertEquals("done")
  }

  dispatcher.test("Utilize a provided Host header".flaky) { dispatcher =>
    val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

    val req = FooRequest.withHeaders(headers.Host("bar.test"))

    getSubmission(req, resp, dispatcher).map { case (request, response) =>
      val requestLines = request.split("\r\n").toList
      assert(requestLines.contains("Host: bar.test"))
      assertEquals(response, "done")
    }
  }

  dispatcher.test("Insert a User-Agent header") { dispatcher =>
    val resp = "HTTP/1.1 200 OK\r\n\r\ndone"

    getSubmission(FooRequest, resp, dispatcher, DefaultUserAgent).map { case (request, response) =>
      val requestLines = request.split("\r\n").toList
      assert(requestLines.contains(s"User-Agent: http4s-blaze/${BuildInfo.version}"))
      assertEquals(response, "done")
    }
  }

  dispatcher.test("Use User-Agent header provided in Request".flaky) { dispatcher =>
    val resp = "HTTP/1.1 200 OK\r\n\r\ndone"
    val req = FooRequest.withHeaders(`User-Agent`(ProductId("myagent")))

    getSubmission(req, resp, dispatcher).map { case (request, response) =>
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
  dispatcher.test("Allow an HTTP/1.0 request without a Host header".ignore) { dispatcher =>
    val resp = "HTTP/1.0 200 OK\r\n\r\ndone"

    val req = Request[IO](uri = www_foo_test, httpVersion = HttpVersion.`HTTP/1.0`)

    getSubmission(req, resp, dispatcher).map { case (request, response) =>
      assert(!request.contains("Host:"))
      assertEquals(response, "done")
    }
  }

  dispatcher.test("Support flushing the prelude") { dispatcher =>
    val req = Request[IO](uri = www_foo_test, httpVersion = HttpVersion.`HTTP/1.0`)
    /*
     * We flush the prelude first to test connection liveness in pooled
     * scenarios before we consume the body.  Make sure we can handle
     * it.  Ensure that we still get a well-formed response.
     */
    getSubmission(req, resp, dispatcher).map(_._2).assertEquals("done")
  }

  fooConnection.test("Not expect body if request was a HEAD request") { tail =>
    val contentLength = 12345L
    val resp = s"HTTP/1.1 200 OK\r\nContent-Length: $contentLength\r\n\r\n"
    val headRequest = FooRequest.withMethod(Method.HEAD)

    val h = new SeqTestHead(List(mkBuffer(resp)))
    LeafBuilder(tail).base(h)

    Resource.suspend(tail.runRequest(headRequest)).use { response =>
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

    dispatcher.test("Support trailer headers") { dispatcher =>
      val hs: IO[Headers] = bracketResponse(req, resp, dispatcher).use { (response: Response[IO]) =>
        for {
          _ <- response.as[String]
          hs <- response.trailerHeaders
        } yield hs
      }

      hs.map(_.headers.mkString).assertEquals("Foo: Bar")
    }

    dispatcher.test("Fail to get trailers before they are complete") { dispatcher =>
      val hs: IO[Headers] = bracketResponse(req, resp, dispatcher).use { (response: Response[IO]) =>
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
      _ <- tail.runRequest(FooRequest) //the first request succeeds
      _ <- IO.sleep(200.millis) // then the server closes the connection
      isClosed <- IO(
        tail.isClosed
      ) // and the client should recognize that the connection has been closed
    } yield assert(isClosed)
  }
}
