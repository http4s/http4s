package org.http4s

import attributes.Key
import scalaz.concurrent.Task
import scalaz.stream.Process._
import scalaz.stream.Process
import org.http4s.Status.Ok

class ExampleRoute[F[_]] extends RouteHandler[F] {
  import BodyParser._

  val flatBigString = (0 until 1000).map{ i => s"This is string number $i" }.foldLeft(""){_ + _}

  object myVar extends Key[String]

  GlobalState(myVar) = "cats"

  def apply(): HttpService[F] = {
    case Get -> Root / "ping" =>
      emit(Response(body = Process.emit("pong").map(s => BodyChunk(s.getBytes))))

    case req @ Get -> Root / ("echo" | "echo2") =>
      emit(Response(body = req.body.map {
        case chunk: BodyChunk => chunk.slice(6, chunk.length)
        case chunk => chunk
      }))

    case req @ Post -> Root / "sum"  =>
      text(req) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      }

    case req =>
      println("Got request that didn't match: " + req.prelude.pathInfo)
      emit(Response(body = Process.emit(s"Didn't find match: ${req.prelude.pathInfo}").map(s => BodyChunk(s.getBytes))))

/*
    case req @ Post -> Root / "sum" =>
      text(req.charset, 16) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      }

    case req @ Get -> Root / "attributes" =>
      req + (myVar, "5")
      Ok("Hello" + req.get(myVar) + ", and " + GlobalState(myVar))

    case Get -> Root / "html" =>
      Ok(
        <html><body>
          <div id="main">
            <h2>Hello world!</h2><br/>
            <h1>This is H1</h1>
          </div>
        </body></html>
      )

    case req @ Get -> Root / "stream" =>
      Ok(Concurrent.unicast[ByteString]({
        channel =>
          new Thread {
            override def run() {
              for (i <- 1 to 10) {
                channel.push(ByteString("%d\n".format(i), req.charset.value))
                Thread.sleep(1000)
              }
              channel.eofAndEnd()
            }
          }.start()

      }))
    case Get -> Root / "bigstring" =>
      emit(Response(body =
        range(0, 1000).map(i => BodyChunk(s"This is string number $i"))
      ))
*/

      /*
    case Get -> Root / "future" =>
      Done{
        Ok(Future("Hello from the future!"))
      }

    case req @ Get -> Root / "bigstring2" =>
      Done{
        Ok(Enumerator((0 until 1000) map { i => ByteString(s"This is string number $i", req.charset.value) }: _*))
      }

    case req @ Get -> Root / "bigstring3" =>
      Done{
        Ok(flatBigString)
      }

    case Get -> Root / "contentChange" =>
      Ok("<h2>This will have an html content type!</h2>", MediaTypes.`text/html`)

      // Ross wins the challenge
    case req @ Get -> Root / "challenge" =>
      Iteratee.head[HttpChunk].map {
        case Some(bits: BodyChunk) if (bits.decodeString(req.charset)).startsWith("Go") =>
          Ok(Enumeratee.heading(Enumerator(bits: HttpChunk)))
        case Some(bits: BodyChunk) if (bits.decodeString(req.charset)).startsWith("NoGo") =>
          BadRequest("Booo!")
        case _ =>
          BadRequest("No data!")
      }

    case req @ Get -> Root / "fail" =>
      sys.error("FAIL")
*/
  }
}