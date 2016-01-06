package com.example.http4s

import java.time.Instant

import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.Date
import org.http4s.scalaxml._
import scodec.bits.ByteVector

import scala.xml.Elem
import scala.concurrent.duration._
import scalaz.{Reducer, Monoid}
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Process._
import scalaz.stream.text.utf8Encode
import scalaz.stream.time.awakeEvery

/** These are routes that we tend to use for testing purposes
  * and will likely get folded into unit tests later in life */
object ScienceExperiments {

  private implicit def timedES = scalaz.concurrent.Strategy.DefaultTimeoutScheduler

  val flatBigString = (0 until 1000).map{ i => s"This is string number $i" }.foldLeft(""){_ + _}

  def service = HttpService {
    ///////////////// Misc //////////////////////
    case req @ POST -> Root / "root-element-name" =>
      req.decode { root: Elem => Ok(root.label) }

    case req @ GET -> Root / "date" =>
      val date = Instant.ofEpochMilli(100)
      Ok(date.toString())
        .putHeaders(Date(date))

    case req @ GET -> Root / "echo-headers" =>
      Ok(req.headers.mkString("\n"))

    ///////////////// Massive Data Loads //////////////////////
    case GET -> Root / "bigstring" =>
      Ok((0 until 1000).map(i => s"This is string number $i").mkString("\n"))

    case req@GET -> Root / "bigstring2" =>
      Ok(Process.range(0, 1000).map(i => s"This is string number $i"))

    case req@GET -> Root / "bigstring3" => Ok(flatBigString)

    case GET -> Root / "zero-chunk" =>
      Ok(Process("", "foo!"))

    case GET -> Root / "bigfile" =>
      val size = 40*1024*1024   // 40 MB
      Ok(new Array[Byte](size))

    case req @ POST -> Root / "rawecho" =>
      // The body can be used in the response
      Ok(req.body)

    ///////////////// Switch the response based on head of content //////////////////////

    case req@POST -> Root / "challenge1" =>
      val body = req.bodyAsText
      def notGo = emit("Booo!!!")
      Ok {
        body.step match {
          case Step(head, tail) =>
            head.runLast.run.fold(tail.continue) { head =>
              if (!head.startsWith("go")) notGo
              else emit(head) ++ tail.continue
            }
          case _ => notGo
        }
      }

    case req @ POST -> Root / "challenge2" =>
      val parser = await1[String] map {
        case chunk if chunk.startsWith("Go") =>
          Task.now(Response(body = emit(chunk) ++ req.bodyAsText |> utf8Encode))
        case chunk if chunk.startsWith("NoGo") =>
          BadRequest("Booo!")
        case _ =>
          BadRequest("no data")
      }
      (req.bodyAsText |> parser).runLastOr(InternalServerError()).run

    /*
      case req @ Post -> Root / "trailer" =>
        trailer(t => Ok(t.headers.length))

      case req @ Post -> Root / "body-and-trailer" =>
        for {
          body <- text(req.charset)
          trailer <- trailer
        } yield Ok(s"$body\n${trailer.headers("Hi").value}")
    */

    ///////////////// Weird Route Failures //////////////////////
    case req @ GET -> Root / "hanging-body" =>
      Ok(Process(Task.now(ByteVector(Seq(' '.toByte))), Task.async[ByteVector] { cb => /* hang */}).eval)

    case req @ GET -> Root / "broken-body" =>
      Ok(Process(Task{"Hello "}) ++ Process(Task{sys.error("Boom!")}) ++ Process(Task{"world!"}))

    case req @ GET -> Root / "slow-body" =>
      val resp = "Hello world!".map(_.toString())
      val body = awakeEvery(2.seconds).zipWith(Process.emitAll(resp))((_, c) => c)
      Ok(body)

    case req @ POST -> Root / "ill-advised-echo" =>
      // Reads concurrently from the input.  Don't do this at home.
      implicit val byteVectorMonoidInstance: Monoid[ByteVector] = Monoid.instance(_ ++ _, ByteVector.empty)
      val tasks = (1 to Runtime.getRuntime.availableProcessors).map(_ => req.body.foldMonoid.runLastOr(ByteVector.empty))
      val result = Task.reduceUnordered(tasks)(Reducer.identityReducer)
      Ok(result)

    case GET -> Root / "fail" / "task" =>
      Task.fail(new RuntimeException)

    case GET -> Root / "fail" / "no-task" =>
      throw new RuntimeException

    case GET -> Root / "fail" / "fatally" =>
      ???

    case req @ GET -> Root / "idle" / IntVar(seconds) =>
      for {
        _    <- Task.delay { Thread.sleep(seconds) }
        resp <- Ok("finally!")
      } yield resp

    case req @ GET -> Root / "connectioninfo" =>
      val conn = req.attributes.get(Request.Keys.ConnectionInfo)

      conn.fold(Ok("Couldn't find connection info!")){ case Request.Connection(loc,rem,secure) =>
        Ok(s"Local: $loc, Remote: $rem, secure: $secure")
      }
  }
}
