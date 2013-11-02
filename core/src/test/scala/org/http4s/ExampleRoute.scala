package org.http4s

import scala.language.reflectiveCalls
import concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee._
import akka.util.ByteString
import org.http4s.dsl._

object ExampleRoute {
  import Status._
  import Writable._
  import BodyParser._

  val MyVar = AttributeKey[String]("myVar")

  def apply(implicit executor: ExecutionContext = ExecutionContext.global): Route = {
    case Get -> Root / "ping" =>
      Ok("pong")

    case Post -> Root / "echo" =>
      Ok(Enumeratee.passAlong[Chunk])

    case Get -> Root / "echo"  =>
      Ok(Enumeratee.map[Chunk] {
        case BodyChunk(e) => BodyChunk(e.slice(6, e.length)): Chunk
        case chunk => chunk
      })

    case Get -> Root / "echo2" =>
      Ok(Enumeratee.map[Chunk]{
        case BodyChunk(e) => BodyChunk(e.slice(6, e.length)): Chunk
        case chunk => chunk
      })

    case req @ Post -> Root / "sum"  =>
      text(req.charset, 16) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      }

    case req @ Post -> Root / "trailer" =>
      trailer(t => Ok(t.headers.length))

    case req @ Post -> Root / "body-and-trailer" =>
      for {
        body <- text(req.charset)
        trailer <- trailer
      } yield Ok(s"$body\n${trailer.headers("Hi").value}")

    case req @ Get -> Root / "stream" =>
      Ok(Concurrent.unicast[ByteString]({
        channel =>
          for (i <- 1 to 10) {
            channel.push(ByteString("%d\n".format(i), req.charset.value))
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      }))

    case Get -> Root / "bigstring" =>
      val builder = new StringBuilder(20*1028)
      Ok((0 until 1000) map { i => s"This is string number $i" })

    case Get -> Root / "future" =>
      Done{
        Ok(Future("Hello from the future!"))
      }

      // Ross wins the challenge
    case req @ Get -> Root / "challenge" =>
      Iteratee.head[Chunk].map {
        case Some(bits: BodyChunk) if (bits.decodeString(req.charset)).startsWith("Go") =>
          Ok(Enumeratee.heading(Enumerator(bits: Chunk)))
        case Some(bits: BodyChunk) if (bits.decodeString(req.charset)).startsWith("NoGo") =>
          BadRequest("Booo!")
        case _ =>
          BadRequest("No data!")
      }

    case req @ Root :/ "root-element-name" =>
      xml(req.charset) { elem =>
        Ok(elem.label)
      }

    case Get -> Root / "html" =>
      Ok(
        <html><body>
          <div id="main">
            <h2>Hello world!</h2><br/>
            <h1>This is H1</h1>
          </div>
        </body></html>
      )

    case Root :/ "fail" =>
      sys.error("FAIL")
  }
}