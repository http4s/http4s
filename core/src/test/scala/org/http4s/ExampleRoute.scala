package org.http4s

import attributes._
import scala.language._
import concurrent.{Future, ExecutionContext}
import akka.util.ByteString
import scalaz.stream.Process
import scalaz.stream.processes
import scalaz.\/
import scalaz.syntax.either._
import scalaz.stream.Process._
import scala.Some
import scala.concurrent.duration._
import scalaz.concurrent.Task
import org.http4s.Status.{Ok, BadRequest}
import scala.xml.{SAXException, XML}
import org.xml.sax.InputSource
import scala.util.Try

object ExampleRoute extends RouteHandler[Task] {
  object myVar extends Key[String]

  def takeBytes(n: Int): Process1[HttpChunk, Response[Nothing] \/ HttpChunk] = {
    await1[HttpChunk] flatMap {
      case chunk @ BodyChunk(bytes) =>
        if (bytes.length > n)
          halt
        else
          emit(chunk.right) then takeBytes(n - bytes.length)
      case chunk =>
        emit(chunk.right) then takeBytes(n)
    }
  }

  def apply(): HttpService[Task] = {
    case Get -> Root / "ping" =>
      Ok("pong").emit

    case req @ Post -> Root / "echo" =>
      Response(body = req.body).emit

    case req @ Get -> Root / ("echo" | "echo2") =>
      Response(body = req.body.map {
        case BodyChunk(e) => BodyChunk(e.slice(6, e.length))
        case chunk => chunk
      }).emit

    case req @ Post -> Root / "sum"  =>
      req.body |> takeBytes(16) |>
        (processes.fromMonoid[HttpChunk].map { chunks =>
          val s = new String(chunks.bytes.toArray, "utf-8")
          Response(body = Process.emit(BodyChunk(s.split('\n').map(_.toInt).sum.toString.getBytes("utf-8"))))
        }).liftR |>
        processes.lift(_.fold(identity _, identity _))

/*
    case req @ Post -> Root / "trailer" =>
      trailer(t => Ok(t.headers.length))

    case req @ Post -> Root / "body-and-trailer" =>
      for {
        body <- text(req.charset)
        trailer <- trailer
      } yield Ok(s"$body\n${trailer.headers("Hi").value}")
*/

    case req @ Get -> Root / "stream" =>
      Response(body =
        awakeEvery(1.second) zip range(0, 10) map { case (_, i) => BodyChunk(i.toString.getBytes("utf-8")) }
      ).emit

    case Get -> Root / "bigstring" =>
      Response(body = range(0, 1000).map(i => BodyChunk(s"This is string number $i".getBytes("utf-8")))).emit

    case Get(Root / "future") =>
      Ok(Task.delay("Hello from the future!")).emit

    /*
    case req @ Get -> Root / "challenge" =>
      req.body |> (await1[HttpChunk] flatMap {
        case bits: BodyChunk if (bits.decodeString(req.prelude.charset)).startsWith("Go") =>
          Process.emit(Response(body = emit(bits) then req.body))
        case bits: BodyChunk if (bits.decodeString(req.prelude.charset)).startsWith("NoGo") =>
          Process.emit(Response(ResponsePrelude(status = Status.BadRequest), body = Process.emit(BodyChunk("Booo!"))))
        case _ =>
          Process.emit(Response(ResponsePrelude(status = Status.BadRequest), body = Process.emit(BodyChunk("no data"))))
      })
    */

    case req @ Root :/ "root-element-name" =>
      req.body |> takeBytes(1024 * 1024) |>
        (processes.fromMonoid[HttpChunk].map { chunks =>
          val in = chunks.bytes.iterator.asInputStream
          val source = new InputSource(in)
          source.setEncoding(req.prelude.charset.value)
          val elem = XML.loadXML(source, XML.parser)
          Response(body = emit(BodyChunk(elem.label.getBytes("utf-8"))))
        }).liftR |>
        processes.lift(_.fold(identity _, identity _))

    case Get -> Root / "html" =>
      Ok(
        <html><body>
          <div id="main">
            <h2>Hello world!</h2><br/>
            <h1>This is H1</h1>
          </div>
        </body></html>
      ).emit

    case Root :/ "fail" =>
      sys.error("FAIL")
  }
}