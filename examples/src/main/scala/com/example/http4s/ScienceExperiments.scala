
package com.example.http4s

import java.time.Instant

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._
import org.http4s.headers.Date
import org.http4s.scalaxml._

import io.circe._
import io.circe.syntax._
import scala.xml.Elem
import scala.concurrent.duration._
import cats.implicits._
import cats.data._
import cats._
import fs2._
import scodec.bits.ByteVector

/** These are routes that we tend to use for testing purposes
  * and will likely get folded into unit tests later in life */
object ScienceExperiments {

  implicit val strategy : fs2.Strategy = fs2.Strategy.fromExecutionContext(scala.concurrent.ExecutionContext.global)
  implicit val scheduler : fs2.Scheduler = fs2.Scheduler.fromFixedDaemonPool(2)


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
      Ok(Stream.range(0, 1000).map(i => s"This is string number $i"))

    case req@GET -> Root / "bigstring3" => Ok(flatBigString)

    case GET -> Root / "zero-chunk" =>
      Ok(Stream("", "foo!"))

    case GET -> Root / "bigfile" =>
      val size = 40*1024*1024   // 40 MB
      Ok(new Array[Byte](size))

    case req @ POST -> Root / "rawecho" =>
      // The body can be used in the response
      Ok(req.body)

    ///////////////// Switch the response based on head of content //////////////////////

//    case req@POST -> Root / "challenge1" =>
//      val body = req.bodyAsText
//      def notGo = Stream.emit("Booo!!!")
//      Ok {
//        body.step match { p => p.flatMap{
//          case Step(head, tail) =>
//            head.runLast.run.fold(tail.continue) { head =>
//              if (!head.startsWith("go")) notGo
//              else emit(head) ++ tail.continue
//            }
//          case _ => notGo
//        }
//      }
//
//    case req @ POST -> Root / "challenge2" =>
//      val parser = await1[String] map {
//        case chunk if chunk.startsWith("Go") =>
//          Task.now(Response(body = emit(chunk) ++ req.bodyAsText |> utf8Encode))
//        case chunk if chunk.startsWith("NoGo") =>
//          BadRequest("Booo!")
//        case _ =>
//          BadRequest("no data")
//      }
//      (req.bodyAsText |> parser).runLastOr(InternalServerError()).run

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
      Ok(Stream.eval(Task.now(ByteVector(Seq(' '.toByte))))
        .evalMap[Task, Task, Byte](_ => Task.async[Byte]{ cb => /* hang */}))

    case req @ GET -> Root / "broken-body" =>
      Ok(Stream.eval(Task{"Hello "}) ++ Stream.eval(Task{sys.error("Boom!")}) ++ Stream.eval(Task{"world!"}))

//    Missing Strategy in Scope Which Is Clearly Not the Case
//    case req @ GET -> Root / "slow-body" =>
//      val resp = "Hello world!".map(_.toString())
//      val body = time.awakeEvery(2.seconds).zipWith(Stream.emits(resp))((_, c) => c)
//      Ok(body)

    case req @ POST -> Root / "ill-advised-echo" =>
      // Reads concurrently from the input.  Don't do this at home.
      implicit val byteVectorMonoidInstance: Monoid[ByteVector] = new Monoid[ByteVector]{
        def combine(x: ByteVector, y: ByteVector): ByteVector = x ++ y
        def empty: ByteVector = ByteVector.empty
      }
      val seq = 1 to Runtime.getRuntime.availableProcessors
      val f : Int => Task[ByteVector] = int => req.body.map(ByteVector.fromByte).runLog.map(_.combineAll)
      val result : Stream[Task, Byte] = Stream.eval(Task.parallelTraverse(seq)(f))
        .flatMap(v => Stream.emits(v.combineAll.toSeq))
      Ok(result)

    case GET -> Root / "fail" / "task" =>
      Task.fail(new RuntimeException)

    case GET -> Root / "fail" / "no-task" =>
      throw new RuntimeException

    case GET -> Root / "fail" / "fatally" =>
      ???

    case req @ GET -> Root / "idle" / LongVar(seconds) =>
      for {
        _    <- Task.delay { Thread.sleep(seconds) }
        resp <- Ok("finally!")
      } yield resp

    case req @ GET -> Root / "connectioninfo" =>
      val conn = req.attributes.get(Request.Keys.ConnectionInfo)

      conn.fold(Ok("Couldn't find connection info!")){ case Request.Connection(loc,rem,secure) =>
        Ok(s"Local: $loc, Remote: $rem, secure: $secure")
      }

    case req @ GET -> Root / "black-knight" / _ =>
      // The servlet examples hide this.
      InternalServerError("Tis but a scratch")

    case req @ POST -> Root / "echo-json" =>
      req.as[Json].flatMap(Ok(_))

    case req @ POST -> Root / "dont-care" =>
      throw new InvalidMessageBodyFailure("lol, I didn't even read it")
  }
}
