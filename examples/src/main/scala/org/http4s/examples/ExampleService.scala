package org.http4s.examples

import org.http4s.Header.`Content-Type`
import org.http4s.json4s.jackson.Json4sJacksonSupport
import org.http4s.server.HttpService
import org.http4s.server.middleware.EntityLimiter
import org.http4s.server.middleware.EntityLimiter.EntityTooLarge
import org.http4s.EntityDecoder._
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._

import scalaz.concurrent.Task
import scalaz.stream.Process, Process.{Get => _, _}
import scala.concurrent.{ExecutionContext, Future}
import org.http4s._
import org.http4s.dsl._
import scodec.bits.ByteVector

object ExampleService extends Http4s with Json4sJacksonSupport {
  import org.http4s.server.middleware.PushSupport._

  val flatBigString = (0 until 1000).map{ i => s"This is string number $i" }.foldLeft(""){_ + _}
  val MyVar = AttributeKey[Int]("org.http4s.examples.myVar")

  def service(implicit executionContext: ExecutionContext = ExecutionContext.global): HttpService =
    service1(executionContext) orElse EntityLimiter(service2, 3)

  def service1(implicit executionContext: ExecutionContext): HttpService = {

    case GET -> Root / "ping" =>
      Ok("pong")

    case req @ GET -> Root / "push" =>
      val data = <html><body><img src="image.jpg"/></body></html>
      Ok(data).push("/image.jpg")(req)

    case req @ GET -> Root / "image.jpg" =>   // Crude: stream doesn't have a binary stream helper yet
      StaticFile.fromResource("/nasa_blackhole_image.jpg", Some(req))
        .map(Task.now)
        .getOrElse(NotFound(req))

    case req @ POST -> Root / "echo" =>
      Task.now(Response(body = req.body))

    case req @ POST -> Root / "echo2" =>
      Task.now(Response(body = req.body.map { chunk =>
        chunk.slice(6, chunk.length)
      }))

    case req @ POST -> Root / "sum"  =>
      text(req).flatMap { s =>
        val sum = s.split('\n').filter(_.length > 0).map(_.trim.toInt).sum
        Ok(sum)
      }

    case req @ POST -> Root / "shortsum"  =>
      text(req).flatMap { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      } handleWith { case EntityTooLarge(_) =>
        Ok("Got a nonfatal Exception, but its OK")
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

    case GET -> Root / "html" =>
      Ok(
        <html><body>
          <div id="main">
            <h2>Hello world!</h2><br/>
            <h1>This is H1</h1>
          </div>
        </body></html>
      )

    case req@ POST -> Root / "challenge" =>
      val body = req.body.map { c => new String(c.toArray, req.charset.nioCharset) }.toTask

      body.flatMap{ s: String =>
        if (!s.startsWith("go")) {
          Ok("Booo!!!")
        } else {
          Ok(emit(s) ++ repeatEval(body))
        }
      }
/*
    case req @ GET -> Root / "stream" =>
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
    case GET -> Root / "bigstring" =>
      Ok((0 until 1000).map(i => s"This is string number $i").mkString("\n"))

    case GET -> Root / "bigfile" =>
      val size = 40*1024*1024   // 40 MB
      Ok(new Array[Byte](size))

    case GET -> Root / "future" =>
      Ok(Future("Hello from the future!"))

    case req @ GET -> Root / "bigstring2" =>
      Ok(Process.range(0, 1000).map(i => s"This is string number $i"))

    case req @ GET -> Root / "bigstring3" => Ok(flatBigString)

    case GET -> Root / "contentChange" =>
      Ok("<h2>This will have an html content type!</h2>", Headers(`Content-Type`(MediaType.`text/html`)))

    case req @ POST -> Root / "challenge" =>
      val parser = await1[ByteVector] map {
        case bits if (new String(bits.toArray, req.charset.nioCharset)).startsWith("Go") =>
          Task.now(Response(body = emit(bits) fby req.body))
        case bits if (new String(bits.toArray, req.charset.nioCharset)).startsWith("NoGo") =>
          BadRequest("Booo!")
        case _ =>
          BadRequest("no data")
      }
      (req.body |> parser).eval.toTask

    case req @ GET -> Root / "root-element-name" =>
      xml(req).flatMap(root => Ok(root.label))

    case req @ GET -> Root / "ip" =>
      Ok("origin" -> req.remoteAddr.getOrElse("unknown"): JValue)

    case req => NotFound(req)
  }

  def service2: HttpService = {
    case req @ POST -> Root / "shortsum"  =>
      text(req).flatMap { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      } handleWith { case EntityTooLarge(_) =>
        Ok("Got a nonfatal Exception, but its OK")
      }
  }
}