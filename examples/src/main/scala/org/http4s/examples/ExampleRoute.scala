package org.http4s

import attributes.{Key, ServerContext}
import scala.language.reflectiveCalls
import concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee._
import org.http4s.Method.Post
import akka.util.ByteString

object ExampleRoute extends RouteHandler {
  import Status._
  import Writable._
  import BodyParser._

  val flatBigString = (0 until 1000).map{ i => s"This is string number $i" }.foldLeft(""){_ + _}

  object myVar extends Key[String]()

  def apply(implicit executor: ExecutionContext = ExecutionContext.global, serverContext: ServerContext = new ServerContext): Route = {
    case req if req.pathInfo == "/ping" =>
      Done(Ok("pong"))

    case Post(req) if req.pathInfo == "/echo" =>
      Done(Ok(Enumeratee.passAlong: Enumeratee[HttpChunk, HttpChunk]))

    case req if req.pathInfo == "/echo" =>
      Done(Ok(Enumeratee.map[HttpChunk] {
        case BodyChunk(e) => BodyChunk(e.slice(6, e.length)): HttpChunk
        case chunk => chunk
      }))

    case req if req.pathInfo == "/echo2" =>
      Done(Ok(Enumeratee.map[HttpChunk] {
        case BodyChunk(e) => BodyChunk(e.slice(6, e.length)): HttpChunk
        case chunk => chunk
      }))

    case Post(req) if req.pathInfo == "/sum" =>
      text(req, 16) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      }

    case req if req.pathInfo == "/stream" =>
      Done(Ok(Concurrent.unicast[ByteString]({
        channel =>
          for (i <- 1 to 10) {
            channel.push(ByteString("%d\n".format(i), req.charset.name))
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      })))

    case req if req.pathInfo == "/bigstring" =>
      Done{
        Ok((0 until 1000) map { i => s"This is string number $i" })
      }

    case req if req.pathInfo == "/future" =>
      Done{
        Ok(Future("Hello from the future!"))
      }

    case req if req.pathInfo == "/bigstring2" =>
      Done{
        Ok(Enumerator((0 until 1000) map { i => ByteString(s"This is string number $i", req.charset.name) }: _*))
      }

    case req if req.pathInfo == "/bigstring3" =>
      Done{
        Ok(flatBigString)
      }

    case req if req.pathInfo == "/contentChange" =>
      Ok("<h2>This will have an html content type!</h2>", MediaTypes.`text/html`)

      // Ross wins the challenge
    case req if req.pathInfo == "/challenge" =>
      Iteratee.head[HttpChunk].map {
        case Some(bits) if (bits.bytes.decodeString(req.charset.name)).startsWith("Go") =>
          Ok(Enumeratee.heading(Enumerator(bits)))
        case Some(bits) if (bits.bytes.decodeString(req.charset.name)).startsWith("NoGo") =>
          BadRequest("Booo!")
        case _ =>
          BadRequest("No data!")
      }

    case req if req.pathInfo == "/fail" =>
      sys.error("FAIL")
  }
}