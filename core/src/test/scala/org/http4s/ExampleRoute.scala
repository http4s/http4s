package org.http4s

import attributes._
import scalaz.stream.Process._
import scala.concurrent.duration._
import scalaz.concurrent.Task
import org.http4s.Status.Ok
import org.http4s.BodyParser._

object ExampleRoute extends RouteHandler {
  object myVar extends Key[String]

  def apply(): HttpService = {
    case Get -> Root / "ping" =>
      Task.now(Ok("pong"))

    case req @ Post -> Root / "echo" =>
      Task.now(Response(body = req.body))

    case req @ Get -> Root / ("echo" | "echo2") =>
      Task.now(Response(body = req.body.map {
        case chunk: BodyChunk => chunk.slice(6, chunk.length)
        case chunk => chunk
      }))

    case req @ Post -> Root / "sum"  =>
      text(req) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      }.toTask

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
      Task.now(Response(body =
        awakeEvery(1.second) zip range(0, 10) map { case (_, i) => BodyChunk(i.toString) }
      ))

    case Get -> Root / "bigstring" =>
      Task.now(Response(body = range(0, 1000).map(i => BodyChunk(s"This is string number $i"))))

/*
    case Get(Root / "future") =>
      Task.now(Ok(Task.delay("Hello from the future!")))
*/

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

    case req @ Root :/ "root-element-name" =>
      req.body |> takeBytes(1024 * 1024) |>
        (processes.fromSemigroup[HttpChunk].map { chunks =>
          val in = chunks.asInputStream
          val source = new InputSource(in)
          source.setEncoding(req.prelude.charset.value)
          val elem = XML.loadXML(source, XML.parser)
          Response(body = emit(BodyChunk(elem.label)))
        }).liftR |>
        processes.lift(_.fold(identity _, identity _))
    */

    case Get -> Root / "html" =>
      Task.now(Ok(
        <html><body>
          <div id="main">
            <h2>Hello world!</h2><br/>
            <h1>This is H1</h1>
          </div>
        </body></html>
      ))

    case Root :/ "fail" =>
      Task.delay(sys.error("FAIL"))
  }
}