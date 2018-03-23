package com.example.http4s

import cats.effect._
import cats.implicits._
import fs2.{Chunk, Pull, Scheduler, Stream}
import io.circe._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Date
import org.http4s.scalaxml._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.xml.Elem

/** These are routes that we tend to use for testing purposes
  * and will likely get folded into unit tests later in life */
class ScienceExperiments[F[_]] extends Http4sDsl[F] {

  val flatBigString: String =
    (0 until 1000)
      .map(i => s"This is string number $i")
      .foldLeft("")(_ + _)

  def service(
      implicit F: Effect[F],
      scheduler: Scheduler,
      executionContext: ExecutionContext = ExecutionContext.global): HttpService[F] =
    HttpService[F] {
      ///////////////// Misc //////////////////////
      case req @ POST -> Root / "root-element-name" =>
        req.decode { root: Elem =>
          Ok(root.label)
        }

      case GET -> Root / "date" =>
        val date = HttpDate.now
        Ok(date.toString, Date(date))

      case req @ GET -> Root / "echo-headers" =>
        Ok(req.headers.mkString("\n"))

      ///////////////// Massive Data Loads //////////////////////
      case GET -> Root / "bigstring" =>
        Ok((0 until 1000).map(i => s"This is string number $i").mkString("\n"))

      case GET -> Root / "bigstring2" =>
        Ok(Stream.range(0, 1000).map(i => s"This is string number $i").covary[F])

      case GET -> Root / "bigstring3" =>
        Ok(flatBigString)

      case GET -> Root / "zero-chunk" =>
        Ok(Stream("", "foo!").covary[F])

      case GET -> Root / "bigfile" =>
        val size = 40 * 1024 * 1024 // 40 MB
        Ok(new Array[Byte](size))

      case req @ POST -> Root / "rawecho" =>
        // The body can be used in the response
        Ok(req.body)

      ///////////////// Switch the response based on head of content //////////////////////

      case req @ POST -> Root / "challenge1" =>
        val body = req.bodyAsText
        def notGo = Stream.emit("Booo!!!")
        def newBodyP(toPull: Stream.ToPull[F, String]): Pull[F, String, Option[Stream[F, String]]] =
          toPull.uncons1.flatMap {
            case Some((s, stream)) =>
              if (s.startsWith("go")) {
                Pull.output1(s).as(Some(stream))
              } else {
                notGo.pull.echo.as(None)
              }
            case None =>
              Pull.pure(None)
          }
        Ok(body.repeatPull(newBodyP))

      case req @ POST -> Root / "challenge2" =>
        def parser(stream: Stream[F, String]): Pull[F, F[Response[F]], Unit] =
          stream.pull.uncons1.flatMap {
            case Some((str, stream)) if str.startsWith("Go") =>
              val body = stream.cons1(str).through(fs2.text.utf8Encode)
              Pull.output1(F.pure(Response(body = body)))
            case Some((str, _)) if str.startsWith("NoGo") =>
              Pull.output1(BadRequest("Booo!"))
            case _ =>
              Pull.output1(BadRequest("no data"))
          }
        parser(req.bodyAsText).stream.compile.last.flatMap(_.getOrElse(InternalServerError()))

      /* TODO
    case req @ POST -> Root / "trailer" =>
      trailer(t => Ok(t.headers.length))

    case req @ POST -> Root / "body-and-trailer" =>
      for {
        body <- text(req.charset)
        trailer <- trailer
      } yield Ok(s"$body\n${trailer.headers("Hi").value}")
       */

      ///////////////// Weird Route Failures //////////////////////
      case GET -> Root / "hanging-body" =>
        Ok(
          Stream
            .eval(F.pure(Chunk.bytes(Array(' '.toByte))))
            .evalMap(_ =>
              F.async[Byte] { cb => /* hang */
            }))

      case GET -> Root / "broken-body" =>
        Ok(
          Stream.eval(F.delay { "Hello " }) ++ Stream.eval(F.delay(sys.error("Boom!"))) ++ Stream
            .eval(F.delay {
              "world!"
            }))

      case GET -> Root / "slow-body" =>
        val resp = "Hello world!".map(_.toString())
        val body = scheduler.awakeEvery[F](2.seconds).zipWith(Stream.emits(resp))((_, c) => c)
        Ok(body)

      case GET -> Root / "fail" / "task" =>
        F.raiseError(new RuntimeException)

      case GET -> Root / "fail" / "no-task" =>
        throw new RuntimeException

      case GET -> Root / "fail" / "fatally" =>
        ???

      case GET -> Root / "idle" / LongVar(seconds) =>
        for {
          _ <- F.delay(Thread.sleep(seconds))
          resp <- Ok("finally!")
        } yield resp

      case req @ GET -> Root / "connectioninfo" =>
        val conn = req.attributes.get(Request.Keys.ConnectionInfo)

        conn.fold(Ok("Couldn't find connection info!")) {
          case Request.Connection(loc, rem, secure) =>
            Ok(s"Local: $loc, Remote: $rem, secure: $secure")
        }

      case GET -> Root / "black-knight" / _ =>
        // The servlet examples hide this.
        InternalServerError("Tis but a scratch")

      case req @ POST -> Root / "echo-json" =>
        req.as[Json].flatMap(Ok(_))

      case POST -> Root / "dont-care" =>
        throw InvalidMessageBodyFailure("lol, I didn't even read it")
    }
}
