package org.http4s
package server
package blaze

import cats.data.Kleisli
import cats.effect._
import cats.effect.concurrent.Deferred
import cats.implicits._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.http4s.{headers => H}
import org.http4s.blaze._
import org.http4s.blaze.pipeline.Command.Connected
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.{ResponseParser, SeqTestHead}
import org.http4s.dsl.io._
import org.http4s.headers.{Date, `Content-Length`, `Transfer-Encoding`}
import org.specs2.specification.AfterAll
import org.specs2.specification.core.Fragment
import scala.concurrent.duration._
import scala.concurrent.Await
import _root_.io.chrisdavenport.vault._

class Http1ServerStageSpec extends Http4sSpec with AfterAll {
  sequential

  val tickWheel = new TickWheelExecutor()

  def afterAll = tickWheel.shutdown()

  def makeString(b: ByteBuffer): String = {
    val p = b.position()
    val a = new Array[Byte](b.remaining())
    b.get(a).position(p)
    new String(a)
  }

  def parseAndDropDate(buff: ByteBuffer): (Status, Set[Header], String) =
    dropDate(ResponseParser.apply(buff))

  def dropDate(resp: (Status, Set[Header], String)): (Status, Set[Header], String) = {
    val hds = resp._2.filter(_.name != Date.name)
    (resp._1, hds, resp._3)
  }

  def runRequest(
      req: Seq[String],
      httpApp: HttpApp[IO],
      maxReqLine: Int = 4 * 1024,
      maxHeaders: Int = 16 * 1024): SeqTestHead = {
    val head = new SeqTestHead(
      req.map(s => ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))))
    val httpStage = Http1ServerStage[IO](
      httpApp,
      () => Vault.empty,
      testExecutionContext,
      enableWebSockets = true,
      maxReqLine,
      maxHeaders,
      10 * 1024,
      DefaultServiceErrorHandler,
      30.seconds,
      30.seconds,
      tickWheel
    )

    pipeline.LeafBuilder(httpStage).base(head)
    head.sendInboundCommand(Connected)
    head
  }

  "Http1ServerStage: Invalid Lengths" should {
    val req = "GET /foo HTTP/1.1\r\nheader: value\r\n\r\n"

    val routes = HttpRoutes
      .of[IO] {
        case _ => Ok("foo!")
      }
      .orNotFound

    "fail on too long of a request line" in {
      val buff = Await.result(runRequest(Seq(req), routes, maxReqLine = 1).result, 5.seconds)
      val str = StandardCharsets.ISO_8859_1.decode(buff.duplicate()).toString
      // make sure we don't have signs of chunked encoding.
      str.contains("400 Bad Request") must_== true
    }

    "fail on too long of a header" in {
      val buff = Await.result(runRequest(Seq(req), routes, maxHeaders = 1).result, 5.seconds)
      val str = StandardCharsets.ISO_8859_1.decode(buff.duplicate()).toString
      // make sure we don't have signs of chunked encoding.
      str.contains("400 Bad Request") must_== true
    }
  }

  "Http1ServerStage: Common responses" should {
    Fragment.foreach(ServerTestRoutes.testRequestResults.zipWithIndex) {
      case ((req, (status, headers, resp)), i) =>
        if (i == 7 || i == 8) // Awful temporary hack
          s"Run request $i Run request: --------\n${req.split("\r\n\r\n")(0)}\n" in {
            val result = Await.result(runRequest(Seq(req), ServerTestRoutes()).result, 5.seconds)
            parseAndDropDate(result) must_== ((status, headers, resp))
          } else
          s"Run request $i Run request: --------\n${req.split("\r\n\r\n")(0)}\n" in {
            val result = Await.result(runRequest(Seq(req), ServerTestRoutes()).result, 5.seconds)
            parseAndDropDate(result) must_== ((status, headers, resp))
          }
    }
  }

  "Http1ServerStage: Errors" should {
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

    def runError(path: String) =
      runRequest(List(path), exceptionService).result
        .map(parseAndDropDate)
        .map {
          case (s, h, r) =>
            val close = h.exists { h =>
              h.toRaw.name == "connection".ci && h.toRaw.value == "close"
            }
            (s, close, r)
        }

    "Deal with synchronous errors" in {
      val path = "GET /sync HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
      val (s, c, _) = Await.result(runError(path), 10.seconds)
      s must_== InternalServerError
      c must_== true
    }

    "Call toHttpResponse on synchronous errors" in {
      val path = "GET /sync/422 HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
      val (s, c, _) = Await.result(runError(path), 10.seconds)
      s must_== UnprocessableEntity
      c must_== false
    }

    "Deal with asynchronous errors" in {
      val path = "GET /async HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
      val (s, c, _) = Await.result(runError(path), 10.seconds)
      s must_== InternalServerError
      c must_== true
    }

    "Call toHttpResponse on asynchronous errors" in {
      val path = "GET /async/422 HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
      val (s, c, _) = Await.result(runError(path), 10.seconds)
      s must_== UnprocessableEntity
      c must_== false
    }

    "Handle parse error" in {
      val path = "THIS\u0000IS\u0000NOT\u0000HTTP"
      val (s, c, _) = Await.result(runError(path), 10.seconds)
      s must_== BadRequest
      c must_== true
    }
  }

  "Http1ServerStage: routes" should {
    "Do not send `Transfer-Encoding: identity` response" in {
      val routes = HttpRoutes
        .of[IO] {
          case _ =>
            val headers = Headers(H.`Transfer-Encoding`(TransferCoding.identity))
            IO.pure(Response[IO](headers = headers)
              .withEntity("hello world"))
        }
        .orNotFound

      // The first request will get split into two chunks, leaving the last byte off
      val req = "GET /foo HTTP/1.1\r\n\r\n"

      val buff = Await.result(runRequest(Seq(req), routes).result, 5.seconds)

      val str = StandardCharsets.ISO_8859_1.decode(buff.duplicate()).toString
      // make sure we don't have signs of chunked encoding.
      str.contains("0\r\n\r\n") must_== false
      str.contains("hello world") must_== true

      val (_, hdrs, _) = ResponseParser.apply(buff)
      hdrs.find(_.name == `Transfer-Encoding`.name) must_== None
    }

    "Do not send an entity or entity-headers for a status that doesn't permit it" in {
      val routes: HttpApp[IO] = HttpRoutes
        .of[IO] {
          case _ =>
            IO.pure(
              Response[IO](status = Status.NotModified)
                .putHeaders(`Transfer-Encoding`(TransferCoding.chunked))
                .withEntity("Foo!"))
        }
        .orNotFound

      val req = "GET /foo HTTP/1.1\r\n\r\n"

      val buf = Await.result(runRequest(Seq(req), routes).result, 5.seconds)
      val (status, hs, body) = ResponseParser.parseBuffer(buf)

      val hss = Headers(hs.toList)
      `Content-Length`.from(hss).isDefined must_== false
      body must_== ""
      status must_== Status.NotModified
    }

    "Add a date header" in {
      val routes = HttpRoutes
        .of[IO] {
          case req => IO.pure(Response(body = req.body))
        }
        .orNotFound

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"

      val buff = Await.result(runRequest(Seq(req1), routes).result, 5.seconds)

      // Both responses must succeed
      val (_, hdrs, _) = ResponseParser.apply(buff)
      hdrs.find(_.name == Date.name) must beSome[Header]
    }

    "Honor an explicitly added date header" in {
      val dateHeader = Date(HttpDate.Epoch)
      val routes = HttpRoutes
        .of[IO] {
          case req => IO.pure(Response(body = req.body).withHeaders(dateHeader))
        }
        .orNotFound

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"

      val buff = Await.result(runRequest(Seq(req1), routes).result, 5.seconds)

      // Both responses must succeed
      val (_, hdrs, _) = ResponseParser.apply(buff)

      hdrs.find(_.name == Date.name) must_== Some(dateHeader)
    }

    "Handle routes that echos full request body for non-chunked" in {
      val routes = HttpRoutes
        .of[IO] {
          case req => IO.pure(Response(body = req.body))
        }
        .orNotFound

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val (r11, r12) = req1.splitAt(req1.length - 1)

      val buff = Await.result(runRequest(Seq(r11, r12), routes).result, 5.seconds)

      // Both responses must succeed
      parseAndDropDate(buff) must_== ((Ok, Set(H.`Content-Length`.unsafeFromLong(4)), "done"))
    }

    "Handle routes that consumes the full request body for non-chunked" in {
      val routes = HttpRoutes
        .of[IO] {
          case req =>
            req.as[String].map { s =>
              Response().withEntity("Result: " + s)
            }
        }
        .orNotFound

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val (r11, r12) = req1.splitAt(req1.length - 1)

      val buff = Await.result(runRequest(Seq(r11, r12), routes).result, 5.seconds)

      // Both responses must succeed
      parseAndDropDate(buff) must_== (
        (
          Ok,
          Set(
            H.`Content-Length`.unsafeFromLong(8 + 4),
            H.`Content-Type`(MediaType.text.plain, Charset.`UTF-8`)),
          "Result: done"))
    }

    "Maintain the connection if the body is ignored but was already read to completion by the Http1Stage" in {

      val routes = HttpRoutes
        .of[IO] {
          case _ => IO.pure(Response().withEntity("foo"))
        }
        .orNotFound

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

      val buff = Await.result(runRequest(Seq(req1, req2), routes).result, 5.seconds)

      val hs = Set(
        H.`Content-Type`(MediaType.text.plain, Charset.`UTF-8`),
        H.`Content-Length`.unsafeFromLong(3))
      // Both responses must succeed
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, hs, "foo"))
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, hs, "foo"))
    }

    "Drop the connection if the body is ignored and was not read to completion by the Http1Stage" in {

      val routes = HttpRoutes
        .of[IO] {
          case _ => IO.pure(Response().withEntity("foo"))
        }
        .orNotFound

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val (r11, r12) = req1.splitAt(req1.length - 1)

      val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

      val buff = Await.result(runRequest(Seq(r11, r12, req2), routes).result, 5.seconds)

      val hs = Set(
        H.`Content-Type`(MediaType.text.plain, Charset.`UTF-8`),
        H.`Content-Length`.unsafeFromLong(3))
      // Both responses must succeed
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, hs, "foo"))
      buff.remaining() must_== 0
    }

    "Handle routes that runs the request body for non-chunked" in {

      val routes = HttpRoutes
        .of[IO] {
          case req => req.body.compile.drain *> IO.pure(Response().withEntity("foo"))
        }
        .orNotFound

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val (r11, r12) = req1.splitAt(req1.length - 1)
      val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

      val buff = Await.result(runRequest(Seq(r11, r12, req2), routes).result, 5.seconds)

      val hs = Set(
        H.`Content-Type`(MediaType.text.plain, Charset.`UTF-8`),
        H.`Content-Length`.unsafeFromLong(3))
      // Both responses must succeed
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, hs, "foo"))
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, hs, "foo"))
    }

    // Think of this as drunk HTTP pipelining
    "Not die when two requests come in back to back" in {

      val routes = HttpRoutes
        .of[IO] {
          case req =>
            IO.pure(Response(body = req.body))
        }
        .orNotFound

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

      val buff = Await.result(runRequest(Seq(req1 + req2), routes).result, 5.seconds)

      // Both responses must succeed
      dropDate(ResponseParser.parseBuffer(buff)) must_== (
        (
          Ok,
          Set(H.`Content-Length`.unsafeFromLong(4)),
          "done"))
      dropDate(ResponseParser.parseBuffer(buff)) must_== (
        (
          Ok,
          Set(H.`Content-Length`.unsafeFromLong(5)),
          "total"))
    }

    "Handle using the request body as the response body" in {

      val routes = HttpRoutes
        .of[IO] {
          case req => IO.pure(Response(body = req.body))
        }
        .orNotFound

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

      val buff = Await.result(runRequest(Seq(req1, req2), routes).result, 5.seconds)

      // Both responses must succeed
      dropDate(ResponseParser.parseBuffer(buff)) must_== (
        (
          Ok,
          Set(H.`Content-Length`.unsafeFromLong(4)),
          "done"))
      dropDate(ResponseParser.parseBuffer(buff)) must_== (
        (
          Ok,
          Set(H.`Content-Length`.unsafeFromLong(5)),
          "total"))
    }

    {
      def req(path: String) =
        s"GET /$path HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n" +
          "3\r\n" +
          "foo\r\n" +
          "0\r\n" +
          "Foo:Bar\r\n\r\n"

      val routes = HttpRoutes
        .of[IO] {
          case req if req.pathInfo == "/foo" =>
            for {
              _ <- req.body.compile.drain
              hs <- req.trailerHeaders
              resp <- Ok(hs.mkString)
            } yield resp

          case req if req.pathInfo == "/bar" =>
            for {
              // Don't run the body
              hs <- req.trailerHeaders
              resp <- Ok(hs.mkString)
            } yield resp
        }
        .orNotFound

      "Handle trailing headers" in {
        val buff = Await.result(runRequest(Seq(req("foo")), routes).result, 5.seconds)

        val results = dropDate(ResponseParser.parseBuffer(buff))
        results._1 must_== Ok
        results._3 must_== "Foo: Bar"
      }

      "Fail if you use the trailers before they have resolved" in {
        val buff = Await.result(runRequest(Seq(req("bar")), routes).result, 5.seconds)

        val results = dropDate(ResponseParser.parseBuffer(buff))
        results._1 must_== InternalServerError
      }
    }
  }

  "cancels on stage shutdown" in skipOnCi {
    Deferred[IO, Unit]
      .flatMap { canceled =>
        Deferred[IO, Unit].flatMap { gate =>
          val req = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
          val app: HttpApp[IO] = HttpApp { req =>
            gate.complete(()) >> IO.cancelable(_ => canceled.complete(()))
          }
          for {
            head <- IO(runRequest(List(req), app))
            _ <- gate.get
            _ <- IO(head.closePipeline(None))
            _ <- canceled.get
          } yield ()
        }
      }
      .unsafeRunTimed(3.seconds) must beSome(())
  }

  "Disconnect if we read an EOF" in {
    val head = runRequest(Seq.empty, Kleisli.liftF(Ok("")))
    Await.ready(head.result, 10.seconds)
    head.closeCauses must_== Seq(None)
  }
}
