package org.http4s

import scala.language.reflectiveCalls
import concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee._
import org.http4s.Method.Post

object ExampleRoute {
  import Status._
  import Writable._

  val flatBigString = (0 until 1000).map{ i => s"This is string number $i" }.foldLeft(""){_ + _}

  def apply(implicit executor: ExecutionContext = ExecutionContext.global): Route = {
    case req if req.pathInfo == "/ping" =>
      Done(Ok("pong"))

    case Post(req) if req.pathInfo == "/echo" =>
      Done(Ok(Enumeratee.passAlong: Enumeratee[HttpChunk, HttpChunk]))

    case req if req.pathInfo == "/echo" =>
      Done(Ok(Enumeratee.map[HttpChunk]{case HttpEntity(e) => HttpEntity(e.slice(6, e.length))}: Enumeratee[HttpChunk, HttpChunk]))

    case req if req.pathInfo == "/echo2" =>
      Done(Ok(Enumeratee.map[HttpChunk]{case HttpEntity(e) => HttpEntity(e.slice(6, e.length))}: Enumeratee[HttpChunk, HttpChunk]))

    case Post(req) if req.pathInfo == "/sum" =>
      stringHandler(req, 16) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      }

    case req if req.pathInfo == "/stream" =>
      Done(Ok(Concurrent.unicast[Raw]({
        channel =>
          for (i <- 1 to 10) {
            channel.push("%d\n".format(i).getBytes)
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
        Ok(Enumerator((0 until 1000) map { i => s"This is string number $i".getBytes }: _*))
      }

    case req if req.pathInfo == "/bigstring3" =>
      Done{
        Ok(flatBigString)
      }

      // Ross wins the challenge
    case req if req.pathInfo == "/challenge" =>
      Iteratee.head[HttpChunk].map {
        case Some(bits) if (new String(bits.bytes)).startsWith("Go") =>
          Ok(Enumeratee.heading(Enumerator(bits)): Enumeratee[HttpChunk, HttpChunk])
        case Some(bits) if (new String(bits.bytes)).startsWith("NoGo") =>
          BadRequest("Booo!")
        case _ =>
          BadRequest("No data!")
      }

    case req if req.pathInfo == "/fail" =>
      sys.error("FAIL")
 }

  def stringHandler(req: RequestPrelude, maxSize: Int = Integer.MAX_VALUE)(f: String => Responder): Iteratee[HttpChunk, Responder] = {
    val it = (Traversable.takeUpTo[Raw](maxSize)
                transform bytesAsString(req)
                flatMap eofOrRequestTooLarge(f)
                map (_.merge))
    Enumeratee.map[HttpChunk](_.bytes) &>> it
  }

  private[this] def bytesAsString(req: RequestPrelude) =
    Iteratee.consume[Raw]().asInstanceOf[Iteratee[Raw, Raw]].map(new String(_, req.charset))

  private[this] def eofOrRequestTooLarge[B](f: String => Responder)(s: String) =
    Iteratee.eofOrElse[B](Responder(ResponsePrelude(status = Status.RequestEntityTooLarge)))(s).map(_.right.map(f))

}