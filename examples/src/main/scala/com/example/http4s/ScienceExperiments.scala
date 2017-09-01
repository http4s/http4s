package com.example.http4s

import java.time.Instant

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers.Date
import org.http4s.scalaxml._
import io.circe._
import io.circe.syntax._

import scala.xml.Elem
import scala.concurrent.duration._
import cats.implicits._
import cats.data._
import cats._
import cats.effect._
import cats.effect.implicits._
import fs2.{text => _, _}
import scodec.bits.ByteVector
import scala.concurrent.ExecutionContext.Implicits.global

/** These are routes that we tend to use for testing purposes
  * and will likely get folded into unit tests later in life */
object ScienceExperiments {

  val scheduler = Scheduler.allocate[IO](corePoolSize = 2).map(_._1).unsafeRunSync()

  val flatBigString = (0 until 1000)
    .map { i =>
      s"This is string number $i"
    }
    .foldLeft("") { _ + _ }

  def service = HttpService[IO] {
    ///////////////// Misc //////////////////////
    case req @ POST -> Root / "root-element-name" =>
      req.decode { root: Elem =>
        Ok(root.label)
      }

    case req @ GET -> Root / "date" =>
      val date = HttpDate.now
      Ok(date.toString()).putHeaders(Date(date))

    case req @ GET -> Root / "echo-headers" =>
      Ok(req.headers.mkString("\n"))

    ///////////////// Massive Data Loads //////////////////////
    case GET -> Root / "bigstring" =>
      Ok((0 until 1000).map(i => s"This is string number $i").mkString("\n"))

    case GET -> Root / "bigstring2" =>
      Ok(Stream.range(0, 1000).map(i => s"This is string number $i").covary[IO])

    case GET -> Root / "bigstring3" =>
      Ok(flatBigString)

    case GET -> Root / "zero-chunk" =>
      Ok(Stream("", "foo!").covary[IO])

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
      def newBodyP(
          toPull: Stream.ToPull[IO, String]): Pull[IO, String, Option[Stream[IO, String]]] =
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
      def parser(stream: Stream[IO, String]): Pull[IO, IO[Response[IO]], Unit] =
        stream.pull.uncons1.flatMap {
          case Some((str, stream)) if str.startsWith("Go") =>
            val body = stream.cons1(str).through(fs2.text.utf8Encode)
            Pull.output1(IO.pure(Response(body = body)))
          case Some((str, _)) if str.startsWith("NoGo") =>
            Pull.output1(BadRequest("Booo!"))
          case _ =>
            Pull.output1(BadRequest("no data"))
        }
      parser(req.bodyAsText).stream.runLast.flatMap(_.getOrElse(InternalServerError()))

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
          .eval(IO.pure(ByteVector(Seq(' '.toByte))))
          .evalMap(_ =>
            IO.async[Byte] { cb => /* hang */
          }))

    case GET -> Root / "broken-body" =>
      Ok(Stream.eval(IO { "Hello " }) ++ Stream.eval(IO(sys.error("Boom!"))) ++ Stream.eval(IO {
        "world!"
      }))

    case GET -> Root / "slow-body" =>
      val resp = "Hello world!".map(_.toString())
      val body = scheduler.awakeEvery[IO](2.seconds).zipWith(Stream.emits(resp))((_, c) => c)
      Ok(body)

    /*
    case req @ POST -> Root / "ill-advised-echo" =>
      // Reads concurrently from the input.  Don't do this at home.
      implicit val byteVectorMonoidInstance: Monoid[ByteVector] = new Monoid[ByteVector]{
        def combine(x: ByteVector, y: ByteVector): ByteVector = x ++ y
        def empty: ByteVector = ByteVector.empty
      }
      val seq = 1 to Runtime.getRuntime.availableProcessors
      val f: Int => IO[ByteVector] = _ => req.body.map(ByteVector.fromByte).runLog.map(_.combineAll)
      val result: Stream[IO, Byte] = Stream.eval(IO.traverse(seq)(f))
        .flatMap(v => Stream.emits(v.combineAll.toSeq))
      Ok(result)
     */

    case GET -> Root / "fail" / "task" =>
      IO.raiseError(new RuntimeException)

    case GET -> Root / "fail" / "no-task" =>
      throw new RuntimeException

    case GET -> Root / "fail" / "fatally" =>
      ???

    case GET -> Root / "idle" / LongVar(seconds) =>
      for {
        _ <- IO(Thread.sleep(seconds))
        resp <- Ok("finally!")
      } yield resp

    case req @ GET -> Root / "connectioninfo" =>
      val conn = req.attributes.get(Request.Keys.ConnectionInfo)

      conn.fold(Ok("Couldn't find connection info!")) {
        case Request.Connection(loc, rem, secure) =>
          Ok(s"Local: $loc, Remote: $rem, secure: $secure")
      }

    case req @ GET -> Root / "black-knight" / _ =>
      // The servlet examples hide this.
      InternalServerError("Tis but a scratch")

    case req @ POST -> Root / "echo-json" =>
      req.as[Json].flatMap(Ok(_))

    case POST -> Root / "dont-care" =>
      throw InvalidMessageBodyFailure("lol, I didn't even read it")
  }
}
