package org.http4s
package examples

import scalaz.concurrent.Task
import scalaz.stream.Process, Process.{Get => PGet, _}
import scalaz.stream.process1
import scala.concurrent.Future
import org.http4s.dsl._
import scala.util.{Failure, Success}
import org.http4s.util.middleware.PushSupport

class ExampleRoute {
  import Status._
  import Writable._
  import BodyParser._
  import PushSupport._

  val flatBigString = (0 until 1000).map{ i => s"This is string number $i" }.foldLeft(""){_ + _}

  import scala.concurrent.ExecutionContext.Implicits.global

  val MyVar = AttributeKey[Int]("org.http4s.examples.myVar")

  def apply(): HttpService = {

    case Get -> Root / "ping" =>
      Ok("pong")

    case Get -> Root / "push" =>
      val data = <html><body><img src="image.jpg"/></body></html>
      Ok(data).push("/http4s/image.jpg")

    case Get -> Root / "image.jpg" =>   // Crude: stream doesn't have a binary stream helper yet
      val bytes = {
        val is = getClass.getResourceAsStream("/nasa_blackhole_image.jpg")
        assert(is != null)
        val buff = new Array[Byte](5000)
        def go(acc: Vector[Array[Byte]]): Array[Byte] = {
          if (is.available() > 0) {
            go(acc :+ buff.slice(0, is.read(buff)))
          }
          else acc.flatten.toArray
        }
        go(Vector.empty)
      }
      Ok(bytes)


    case req @ Post -> Root / "echo" =>
      Task.now(Response(body = req.body))

    case req @ Post -> Root / "echo2" =>
      Task.now(Response(body = req.body.map {
        case chunk: BodyChunk => chunk.slice(6, chunk.length)
        case chunk => chunk
      }))


    case req @ Post -> Root / "sum"  =>
      text(req).flatMap{ s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      }

    case req @ Post -> Root / "shortsum"  =>
      text(req, limit = 3).flatMap { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      } handle { case EntityTooLarge(_) =>
        Ok("Got a nonfatal Exception, but its OK").run
      }

/*
    case req @ Post -> Root / "trailer" =>
      trailer(t => Ok(t.headers.length))

    case req @ Post -> Root / "body-and-trailer" =>
      for {
        body <- text(req.charset)
        trailer <- trailer
      } yield Ok(s"$body\n${trailer.headers("Hi").value}")
*/

    case Get -> Root / "html" =>
      Ok(
        <html><body>
          <div id="main">
            <h2>Hello world!</h2><br/>
            <h1>This is H1</h1>
          </div>
        </body></html>
      )

    case req@ Post -> Root / "challenge" =>
      val body = req.body.collect {
        case c: BodyChunk => new String(c.toArray)
      }.toTask

      body.flatMap{ s: String =>
        if (!s.startsWith("go")) {
          Ok("Booo!!!")
        } else {
          Ok(emit(s) ++ repeatEval(body))
        }
      }
/*
    case req @ Get -> Root / "stream" =>
      Ok(Concurrent.unicast[ByteString]({
        channel =>
          new Thread {
            override def run() {
              for (i <- 1 to 10) {
                channel.push(ByteString("%d\n".format(i), req.charset.name))
                Thread.sleep(1000)
              }
              channel.eofAndEnd()
            }
          }.start()

      }))
  */
    case Get -> Root / "bigstring" =>
      Ok((0 until 1000).map(i => s"This is string number $i"))

    case Get -> Root / "bigfile" =>
      val size = 40*1024*1024   // 40 MB
      Ok(new Array[Byte](size))

    case Get -> Root / "future" =>
      Ok(Future("Hello from the future!"))

    case req @ Get -> Root / "bigstring2" =>
      Ok(Process.range(0, 1000).map(i => s"This is string number $i"))

    case req @ Get -> Root / "bigstring3" => Ok(flatBigString)

    case Get -> Root / "contentChange" =>
      Ok("<h2>This will have an html content type!</h2>", MediaType.`text/html`)

    case req @ Post -> Root / "challenge" =>
      val parser = await1[Chunk] map {
        case bits: BodyChunk if (bits.decodeString(req.charset)).startsWith("Go") =>
          Task.now(Response(body = emit(bits) fby req.body))
        case bits: BodyChunk if (bits.decodeString(req.charset)).startsWith("NoGo") =>
          BadRequest("Booo!")
        case _ =>
          BadRequest("no data")
      }
      (req.body |> parser).eval.toTask

    case req @ Get -> Root / "root-element-name" =>
      xml(req).flatMap(root => Ok(root.label))

    case req @ Get -> Root / "fail" =>
      sys.error("FAIL")

    case req => NotFound(req)
  }
}