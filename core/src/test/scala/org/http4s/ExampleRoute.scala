package org.http4s

import scala.language.reflectiveCalls
import concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee._
import akka.util.ByteString

object ExampleRoute {
  import Status._
  import Writable._
  import BodyParser._

  val MyVar = AttributeKey[String]("myVar")

  /*
   * We can't see the dsl package from core.  This is an ad hoc thing
   * to make this test a little easier to write.
   */
  object Req {
    def unapply(req: RequestPrelude): Option[(Method, String)] = Some(req.requestMethod, req.pathInfo)
  }

  import Method._

  import PushSupport._

  def apply(implicit executor: ExecutionContext = ExecutionContext.global): Route = {

    case Req(Get, "/push") =>
      Ok("Pushing: ").push("/pushed")

    case Req(Get, "/pushed") =>
      Ok("Pushed").push("/ping")

    case Req(Get, "/ping") =>
      Ok("pong")

    case Req(Post, "/echo") =>
      Ok(Enumeratee.passAlong[Chunk])

    case Req(Get, "/echo") =>
      Ok(Enumeratee.map[Chunk] {
        case BodyChunk(e) => BodyChunk(e.slice(6, e.length)): Chunk
        case chunk => chunk
      })

    case Req(Get, "/echo2") =>
      Ok(Enumeratee.map[Chunk]{
        case BodyChunk(e) => BodyChunk(e.slice(6, e.length)): Chunk
        case chunk => chunk
      })

    case req @ Req(Post, "/sum")  =>
      text(req.charset, 16) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      }

    case Req(Post, "/trailer") =>
      trailer(t => Ok(t.headers.length))

    case req @ Req(Post, "/body-and-trailer") =>
      for {
        body <- text(req.charset)
        trailer <- trailer
      } yield trailer.headers.find(_.name == "hi".ci).fold(InternalServerError()){ hi =>  Ok(s"$body\n${hi.value}") }

    case req @ Req(Get, "/stream") =>
      Ok(Concurrent.unicast[ByteString]({
        channel =>
          for (i <- 1 to 10) {
            channel.push(ByteString("%d\n".format(i), req.charset.name))
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      }))

    case Req(Get, "/bigstring") =>
      val builder = new StringBuilder(20*1028)
      Ok((0 until 1000) map { i => s"This is string number $i" })

    case Req(Get, "/future") =>
      Done{
        Ok(Future("Hello from the future!"))
      }

      // Ross wins the challenge
    case req @ Req(Get, "/challenge") =>
      Iteratee.head[Chunk].map {
        case Some(bits: BodyChunk) if (bits.decodeString(req.charset)).startsWith("Go") =>
          Ok(Enumeratee.heading(Enumerator(bits: Chunk)))
        case Some(bits: BodyChunk) if (bits.decodeString(req.charset)).startsWith("NoGo") =>
          BadRequest("Booo!")
        case _ =>
          BadRequest("No data!")
      }

    case req @ Req(Get, "/root-element-name") =>
      xml(req.charset) { elem =>
        Ok(elem.label)
      }

    case Req(Get, "/html") =>
      Ok(
        <html><body>
          <div id="main">
            <h2>Hello world!</h2><br/>
            <h1>This is H1</h1>
          </div>
        </body></html>
      )

    case Req(Get, "/fail") =>
      sys.error("FAIL")
  }
}