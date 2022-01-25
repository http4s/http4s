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
package server

import cats.data.Kleisli
import cats.effect._
import cats.effect.kernel.Deferred
import cats.effect.std.Dispatcher
import cats.syntax.all._
import org.http4s.blaze.pipeline.Command.Connected
import org.http4s.blaze.pipeline.Command.Disconnected
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.ResponseParser
import org.http4s.blazecore.SeqTestHead
import org.http4s.dsl.io._
import org.http4s.headers.Date
import org.http4s.headers.`Content-Length`
import org.http4s.headers.`Transfer-Encoding`
import org.http4s.syntax.all._
import org.http4s.testing.ErrorReporting._
import org.http4s.websocket.WebSocketContext
import org.http4s.{headers => H}
import org.typelevel.ci._
import org.typelevel.vault._

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.annotation.nowarn
import scala.concurrent.duration._

class Http1ServerStageSpec extends Http4sSuite {

  val fixture = ResourceFixture(Resource.make(IO.delay(new TickWheelExecutor())) { twe =>
    IO.delay(twe.shutdown())
  })

  // todo replace with DispatcherIOFixture
  val dispatcher = new Fixture[Dispatcher[IO]]("dispatcher") {

    private var d: Dispatcher[IO] = null
    private var shutdown: IO[Unit] = null
    def apply() = d
    override def beforeAll(): Unit = {
      val dispatcherAndShutdown = Dispatcher[IO].allocated.unsafeRunSync()
      shutdown = dispatcherAndShutdown._2
      d = dispatcherAndShutdown._1
    }
    override def afterAll(): Unit =
      shutdown.unsafeRunSync()
  }
  override def munitFixtures = List(dispatcher)

  def makeString(b: ByteBuffer): String = {
    val p = b.position()
    val a = new Array[Byte](b.remaining())
    b.get(a).position(p)
    new String(a)
  }

  def parseAndDropDate(buff: ByteBuffer): (Status, Set[Header.Raw], String) =
    dropDate(ResponseParser.apply(buff))

  def dropDate(resp: (Status, Set[Header.Raw], String)): (Status, Set[Header.Raw], String) = {
    val hds = resp._2.filter(_.name != Header[Date].name)
    (resp._1, hds, resp._3)
  }

  def runRequest(
      tw: TickWheelExecutor,
      req: Seq[String],
      httpApp: HttpApp[IO],
      maxReqLine: Int = 4 * 1024,
      maxHeaders: Int = 16 * 1024,
  ): SeqTestHead = {
    val head = new SeqTestHead(
      req.map(s => ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1)))
    )
    val httpStage = server.Http1ServerStage[IO](
      httpApp,
      () => Vault.empty,
      munitExecutionContext,
      wsKey = Key.newKey[SyncIO, WebSocketContext[IO]].unsafeRunSync(),
      maxReqLine,
      maxHeaders,
      10 * 1024,
      silentErrorHandler,
      30.seconds,
      30.seconds,
      tw,
      dispatcher(),
      None,
    )

    pipeline.LeafBuilder(httpStage).base(head)
    head.sendInboundCommand(Connected)
    head
  }

  val req = "GET /foo HTTP/1.1\r\nheader: value\r\n\r\n"

  val routes = HttpRoutes
    .of[IO] { case _ =>
      Ok("foo!")
    }
    .orNotFound

  fixture.test("Http1ServerStage: Invalid Lengths should fail on too long of a request line") {
    tickwheel =>
      runRequest(tickwheel, Seq(req), routes, maxReqLine = 1).result.map { buff =>
        val str = StandardCharsets.ISO_8859_1.decode(buff.duplicate()).toString
        // make sure we don't have signs of chunked encoding.
        assert(str.contains("400 Bad Request"))
      }
  }

  fixture.test("Http1ServerStage: Invalid Lengths should fail on too long of a header") {
    tickwheel =>
      runRequest(tickwheel, Seq(req), routes, maxHeaders = 1).result.map { buff =>
        val str = StandardCharsets.ISO_8859_1.decode(buff.duplicate()).toString
        // make sure we don't have signs of chunked encoding.
        assert(str.contains("400 Bad Request"))
      }
  }

  ServerTestRoutes.testRequestResults.zipWithIndex.foreach {
    case ((req, (status, headers, resp)), i) =>
      if (i == 7 || i == 8) // Awful temporary hack
        fixture.test(
          s"Http1ServerStage: Common responses should Run request $i Run request: --------\n${req
            .split("\r\n\r\n")(0)}\n"
        ) { tw =>
          runRequest(tw, Seq(req), ServerTestRoutes()).result
            .map(parseAndDropDate)
            .map(assertEquals(_, (status, headers, resp)))

        }
      else
        fixture.test(
          s"Http1ServerStage: Common responses should Run request $i Run request: --------\n${req
            .split("\r\n\r\n")(0)}\n"
        ) { tw =>
          runRequest(tw, Seq(req), ServerTestRoutes()).result
            .map(parseAndDropDate)
            .map(assertEquals(_, (status, headers, resp)))

        }
  }

  val exceptionService = HttpRoutes
    .of[IO] {
      case GET -> Root / "sync" => sys.error("Synchronous error!")
      case GET -> Root / "async" => IO.raiseError(new Exception("Asynchronous error!"))
      case GET -> Root / "sync" / "422" =>
        throw InvalidMessageBodyFailure("lol, I didn't even look")
      case GET -> Root / "async" / "422" =>
        IO.raiseError(InvalidMessageBodyFailure("lol, I didn't even look"))
    }
    .orNotFound

  def runError(tw: TickWheelExecutor, path: String) =
    runRequest(tw, List(path), exceptionService).result
      .map(parseAndDropDate)
      .map { case (s, h, r) =>
        val close = h.exists { h =>
          h.name == ci"connection" && h.value == "close"
        }
        (s, close, r)
      }

  fixture.test("Http1ServerStage: Errors should Deal with synchronous errors") { tw =>
    val path = "GET /sync HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
    runError(tw, path).map { case (s, c, _) =>
      assert(c)
      assertEquals(s, InternalServerError)
    }
  }

  fixture.test("Http1ServerStage: Errors should Call toHttpResponse on synchronous errors") { tw =>
    val path = "GET /sync/422 HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
    runError(tw, path).map { case (s, c, _) =>
      assert(!c)
      assertEquals(s, UnprocessableEntity)
    }
  }

  fixture.test("Http1ServerStage: Errors should Deal with asynchronous errors") { tw =>
    val path = "GET /async HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
    runError(tw, path).map { case (s, c, _) =>
      assert(c)
      assertEquals(s, InternalServerError)
    }
  }

  fixture.test("Http1ServerStage: Errors should Call toHttpResponse on asynchronous errors") { tw =>
    val path = "GET /async/422 HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
    runError(tw, path).map { case (s, c, _) =>
      assert(!c)
      assertEquals(s, UnprocessableEntity)
    }
  }

  fixture.test("Http1ServerStage: Errors should Handle parse error") { tw =>
    val path = "THIS\u0000IS\u0000NOT\u0000HTTP"
    runError(tw, path).map { case (s, c, _) =>
      assert(c)
      assertEquals(s, BadRequest)
    }
  }

  fixture.test(
    "Http1ServerStage: routes should Do not send `Transfer-Encoding: identity` response"
  ) { tw =>
    val routes = HttpRoutes
      .of[IO] { case _ =>
        val headers = Headers(H.`Transfer-Encoding`(TransferCoding.identity))
        IO.pure(
          Response[IO](headers = headers)
            .withEntity("hello world")
        )
      }
      .orNotFound

    // The first request will get split into two chunks, leaving the last byte off
    val req = "GET /foo HTTP/1.1\r\n\r\n"

    runRequest(tw, Seq(req), routes).result.map { buff =>
      val str = StandardCharsets.ISO_8859_1.decode(buff.duplicate()).toString
      // make sure we don't have signs of chunked encoding.
      assert(!str.contains("0\r\n\r\n"))
      assert(str.contains("hello world"))

      val (_, hdrs, _) = ResponseParser.apply(buff)
      assert(!hdrs.exists(_.name == `Transfer-Encoding`.name))
    }
  }

  fixture.test(
    "Http1ServerStage: routes should Do not send an entity or entity-headers for a status that doesn't permit it"
  ) { tw =>
    val routes: HttpApp[IO] = HttpRoutes
      .of[IO] { case _ =>
        IO.pure(
          Response[IO](status = Status.NotModified)
            .putHeaders(`Transfer-Encoding`(TransferCoding.chunked))
            .withEntity("Foo!")
        )
      }
      .orNotFound

    val req = "GET /foo HTTP/1.1\r\n\r\n"

    runRequest(tw, Seq(req), routes).result.map { buf =>
      val (status, hs, body) = ResponseParser.parseBuffer(buf)
      hs.foreach { h =>
        assert(`Content-Length`.parse(h.value).isLeft)
      }
      assertEquals(body, "")
      assertEquals(status, Status.NotModified)
    }
  }

  fixture.test("Http1ServerStage: routes should Add a date header") { tw =>
    val routes = HttpRoutes
      .of[IO] { case req =>
        IO.pure(Response(body = req.body))
      }
      .orNotFound

    // The first request will get split into two chunks, leaving the last byte off
    val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"

    runRequest(tw, Seq(req1), routes).result.map { buff =>
      // Both responses must succeed
      val (_, hdrs, _) = ResponseParser.apply(buff)
      assert(hdrs.exists(_.name == Header[Date].name))
    }
  }

  fixture.test("Http1ServerStage: routes should Honor an explicitly added date header") { tw =>
    val dateHeader = Date(HttpDate.Epoch)
    val routes = HttpRoutes
      .of[IO] { case req =>
        IO.pure(Response(body = req.body).withHeaders(dateHeader))
      }
      .orNotFound

    // The first request will get split into two chunks, leaving the last byte off
    val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"

    runRequest(tw, Seq(req1), routes).result.map { buff =>
      // Both responses must succeed
      val (_, hdrs, _) = ResponseParser.apply(buff)

      val result = hdrs.find(_.name == Header[Date].name).map(_.value)
      assertEquals(result, Some(dateHeader.value))
    }
  }

  fixture.test(
    "Http1ServerStage: routes should Handle routes that echos full request body for non-chunked"
  ) { tw =>
    val routes = HttpRoutes
      .of[IO] { case req =>
        IO.pure(Response(body = req.body))
      }
      .orNotFound

    // The first request will get split into two chunks, leaving the last byte off
    val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
    val (r11, r12) = req1.splitAt(req1.length - 1)

    runRequest(tw, Seq(r11, r12), routes).result.map { buff =>
      // Both responses must succeed
      assertEquals(
        parseAndDropDate(buff),
        (Ok, Set(H.`Content-Length`.unsafeFromLong(4).toRaw1), "done"),
      )
    }
  }

  fixture.test(
    "Http1ServerStage: routes should Handle routes that consumes the full request body for non-chunked"
  ) { tw =>
    val routes = HttpRoutes
      .of[IO] { case req =>
        req.as[String].map { s =>
          Response().withEntity("Result: " + s)
        }
      }
      .orNotFound

    // The first request will get split into two chunks, leaving the last byte off
    val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
    val (r11, r12) = req1.splitAt(req1.length - 1)

    runRequest(tw, Seq(r11, r12), routes).result.map { buff =>
      // Both responses must succeed
      assertEquals(
        parseAndDropDate(buff),
        (
          Ok,
          Set(
            H.`Content-Length`.unsafeFromLong(8 + 4).toRaw1,
            H.`Content-Type`(MediaType.text.plain, Charset.`UTF-8`).toRaw1,
          ),
          "Result: done",
        ),
      )
    }
  }

  fixture.test(
    "Http1ServerStage: routes should Maintain the connection if the body is ignored but was already read to completion by the Http1Stage"
  ) { tw =>
    val routes = HttpRoutes
      .of[IO] { case _ =>
        IO.pure(Response().withEntity("foo"))
      }
      .orNotFound

    // The first request will get split into two chunks, leaving the last byte off
    val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
    val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

    runRequest(tw, Seq(req1, req2), routes).result.map { buff =>
      val hs = Set(
        H.`Content-Type`(MediaType.text.plain, Charset.`UTF-8`).toRaw1,
        H.`Content-Length`.unsafeFromLong(3).toRaw1,
      )
      // Both responses must succeed
      assertEquals(dropDate(ResponseParser.parseBuffer(buff)), (Ok, hs, "foo"))
      assertEquals(dropDate(ResponseParser.parseBuffer(buff)), (Ok, hs, "foo"))
    }
  }

  fixture.test(
    "Http1ServerStage: routes should Drop the connection if the body is ignored and was not read to completion by the Http1Stage"
  ) { tw =>
    val routes = HttpRoutes
      .of[IO] { case _ =>
        IO.pure(Response().withEntity("foo"))
      }
      .orNotFound

    // The first request will get split into two chunks, leaving the last byte off
    val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
    val (r11, r12) = req1.splitAt(req1.length - 1)

    val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

    runRequest(tw, Seq(r11, r12, req2), routes).result.map { buff =>
      val hs = Set(
        H.`Content-Type`(MediaType.text.plain, Charset.`UTF-8`).toRaw1,
        H.`Content-Length`.unsafeFromLong(3).toRaw1,
      )
      // Both responses must succeed
      assertEquals(dropDate(ResponseParser.parseBuffer(buff)), (Ok, hs, "foo"))
      assertEquals(buff.remaining(), 0)
    }
  }

  fixture.test(
    "Http1ServerStage: routes should Handle routes that runs the request body for non-chunked"
  ) { tw =>
    val routes = HttpRoutes
      .of[IO] { case req =>
        req.body.compile.drain *> IO.pure(Response().withEntity("foo"))
      }
      .orNotFound

    // The first request will get split into two chunks, leaving the last byte off
    val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
    val (r11, r12) = req1.splitAt(req1.length - 1)
    val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

    runRequest(tw, Seq(r11, r12, req2), routes).result.map { buff =>
      val hs = Set(
        H.`Content-Type`(MediaType.text.plain, Charset.`UTF-8`).toRaw1,
        H.`Content-Length`.unsafeFromLong(3).toRaw1,
      )
      // Both responses must succeed
      assertEquals(dropDate(ResponseParser.parseBuffer(buff)), (Ok, hs, "foo"))
      assertEquals(dropDate(ResponseParser.parseBuffer(buff)), (Ok, hs, "foo"))
    }
  }

  // Think of this as drunk HTTP pipelining
  fixture.test("Http1ServerStage: routes should Not die when two requests come in back to back") {
    tw =>
      val routes = HttpRoutes
        .of[IO] { case req =>
          IO.pure(Response(body = req.body))
        }
        .orNotFound

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

      runRequest(tw, Seq(req1 + req2), routes).result.map { buff =>
        // Both responses must succeed
        assertEquals(
          dropDate(ResponseParser.parseBuffer(buff)),
          (Ok, Set(H.`Content-Length`.unsafeFromLong(4).toRaw1), "done"),
        )
        assertEquals(
          dropDate(ResponseParser.parseBuffer(buff)),
          (Ok, Set(H.`Content-Length`.unsafeFromLong(5).toRaw1), "total"),
        )
      }
  }

  fixture.test(
    "Http1ServerStage: routes should Handle using the request body as the response body"
  ) { tw =>
    val routes = HttpRoutes
      .of[IO] { case req =>
        IO.pure(Response(body = req.body))
      }
      .orNotFound

    // The first request will get split into two chunks, leaving the last byte off
    val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
    val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

    runRequest(tw, Seq(req1, req2), routes).result.map { buff =>
      // Both responses must succeed
      assertEquals(
        dropDate(ResponseParser.parseBuffer(buff)),
        (Ok, Set(H.`Content-Length`.unsafeFromLong(4).toRaw1), "done"),
      )
      assertEquals(
        dropDate(ResponseParser.parseBuffer(buff)),
        (Ok, Set(H.`Content-Length`.unsafeFromLong(5).toRaw1), "total"),
      )
    }
  }

  def req(path: String) =
    s"POST /$path HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n" +
      "3\r\n" +
      "foo\r\n" +
      "0\r\n" +
      "Foo:Bar\r\n\r\n"

  val routes2 = HttpRoutes
    .of[IO] {
      case req if req.pathInfo === path"/foo" =>
        for {
          _ <- req.body.compile.drain
          hs <- req.trailerHeaders
          resp <- Ok(hs.headers.mkString)
        } yield resp

      case req if req.pathInfo === path"/bar" =>
        for {
          // Don't run the body
          hs <- req.trailerHeaders
          resp <- Ok(hs.headers.mkString)
        } yield resp
    }
    .orNotFound

  fixture.test("Http1ServerStage: routes should Handle trailing headers") { tw =>
    (runRequest(tw, Seq(req("foo")), routes2).result).map { buff =>
      val results = dropDate(ResponseParser.parseBuffer(buff))
      assertEquals(results._1, Ok)
      assertEquals(results._3, "Foo: Bar")
    }
  }

  fixture.test(
    "Http1ServerStage: routes should Fail if you use the trailers before they have resolved"
  ) { tw =>
    runRequest(tw, Seq(req("bar")), routes2).result.map { buff =>
      val results = dropDate(ResponseParser.parseBuffer(buff))
      assertEquals(results._1, InternalServerError)
    }
  }

  fixture.test("Http1ServerStage: routes should cancels on stage shutdown".flaky) { tw =>
    Deferred[IO, Unit]
      .flatMap { canceled =>
        Deferred[IO, Unit].flatMap { gate =>
          val req =
            "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
          val app: HttpApp[IO] = HttpApp { _ =>
            gate.complete(()) >> canceled.complete(()) >> IO.never[Response[IO]]
          }
          for {
            head <- IO(runRequest(tw, List(req), app))
            _ <- gate.get
            _ <- IO(head.closePipeline(None))
            _ <- canceled.get
          } yield ()
        }
      }
  }

  fixture.test("Http1ServerStage: routes should Disconnect if we read an EOF") { tw =>
    val head = runRequest(tw, Seq.empty, Kleisli.liftF(Ok("")))
    head.result.map { _ =>
      assertEquals(head.closeCauses, Vector(None))
    }
  }

  fixture.test("Prevent response splitting attacks on status reason phrase") { tw =>
    val rawReq = "GET /?reason=%0D%0AEvil:true%0D%0A HTTP/1.0\r\n\r\n"
    @nowarn("cat=deprecation")
    val head = runRequest(
      tw,
      List(rawReq),
      HttpApp { req =>
        Response[IO](Status.NoContent.withReason(req.params("reason"))).pure[IO]
      },
    )
    head.result.map { buff =>
      val (_, headers, _) = ResponseParser.parseBuffer(buff)
      assertEquals(headers.find(_.name === ci"Evil"), None)
    }
  }

  fixture.test("Prevent response splitting attacks on field name") { tw =>
    val rawReq = "GET /?fieldName=Fine:%0D%0AEvil:true%0D%0A HTTP/1.0\r\n\r\n"
    val head = runRequest(
      tw,
      List(rawReq),
      HttpApp { req =>
        Response[IO](Status.NoContent).putHeaders(req.params("fieldName") -> "oops").pure[IO]
      },
    )
    head.result.map { buff =>
      val (_, headers, _) = ResponseParser.parseBuffer(buff)
      assertEquals(headers.find(_.name === ci"Evil"), None)
    }
  }

  fixture.test("Prevent response splitting attacks on field value") { tw =>
    val rawReq = "GET /?fieldValue=%0D%0AEvil:true%0D%0A HTTP/1.0\r\n\r\n"
    val head = runRequest(
      tw,
      List(rawReq),
      HttpApp { req =>
        Response[IO](Status.NoContent)
          .putHeaders("X-Oops" -> req.params("fieldValue"))
          .pure[IO]
      },
    )
    head.result.map { buff =>
      val (_, headers, _) = ResponseParser.parseBuffer(buff)
      assertEquals(headers.find(_.name === ci"Evil"), None)
    }

    fixture.test("Http1ServerStage: don't deadlock TickWheelExecutor with uncancelable request") {
      tw =>
        val reqUncancelable = List("GET /uncancelable HTTP/1.0\r\n\r\n")
        val reqCancelable = List("GET /cancelable HTTP/1.0\r\n\r\n")

        (for {
          uncancelableStarted <- Deferred[IO, Unit]
          uncancelableCanceled <- Deferred[IO, Unit]
          cancelableStarted <- Deferred[IO, Unit]
          cancelableCanceled <- Deferred[IO, Unit]
          app = HttpApp[IO] {
            case req if req.pathInfo === path"/uncancelable" =>
              uncancelableStarted.complete(()) *>
                IO.uncancelable { poll =>
                  poll(uncancelableCanceled.complete(())) *>
                    cancelableCanceled.get
                }.as(Response[IO]())
            case _ =>
              cancelableStarted.complete(()) *> IO.never.guarantee(
                cancelableCanceled.complete(()).void
              )
          }
          head <- IO(runRequest(tw, reqUncancelable, app))
          _ <- uncancelableStarted.get
          _ <- uncancelableCanceled.get
          _ <- IO(head.sendInboundCommand(Disconnected))
          head2 <- IO(runRequest(tw, reqCancelable, app))
          _ <- cancelableStarted.get
          _ <- IO(head2.sendInboundCommand(Disconnected))
          _ <- cancelableCanceled.get
        } yield ()).assert
    }
  }
}
