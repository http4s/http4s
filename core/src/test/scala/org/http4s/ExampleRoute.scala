package org.http4s

import scala.language.reflectiveCalls
import scala.concurrent.ExecutionContext
import play.api.libs.iteratee._
import org.http4s.Method.Post

object ExampleRoute {
  import StatusLine._
  import Writable._

  def apply(implicit executor: ExecutionContext = ExecutionContext.global): Route = {
    case req if req.pathInfo == "/ping" =>
      Done(Ok("pong"))

    case Post(req) if req.pathInfo == "/echo" =>
      Done(Ok.transform(Enumeratee.passAlong))

    case req if req.pathInfo == "/echo" =>
      Done(Ok.transform(Enumeratee.map[HttpChunk]{case HttpEntity(e) => HttpEntity(e.slice(6, e.length))}))

    case req if req.pathInfo == "/echo2" =>
      Done(Ok.transform(Enumeratee.map[HttpChunk]{case HttpEntity(e) => HttpEntity(e.slice(6, e.length))}))

    case Post(req) if req.pathInfo == "/sum" =>
      stringHandler(req, 16) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      }

    case req if req.pathInfo == "/stream" =>
      Done(Ok.feed(Concurrent.unicast[Raw]({
        channel =>
          for (i <- 1 to 10) {
            channel.push("%d\n".format(i).getBytes)
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      })))

    case req if req.pathInfo == "/bigstring" =>
      Done{
        val builder = new StringBuilder(20*1028)
        Ok((0 until 1000) map { i => s"This is string number $i" })
      }

    /*
    // Reads the whole body before responding
    case req if req.pathInfo == "/determine_echo1" =>
      req.body.run( Iteratee.getChunks).map { bytes =>
        Responder( body = Enumerator(bytes.map(HttpEntity(_)):_*))
      }

    // Demonstrate how simple it is to read some and then continue
    case req if req.pathInfo == "/determine_echo2" =>
      val bit: Future[Option[Raw]] = req.body.run(Iteratee.head)
      bit.map {
        case Some(bit) => Responder( body = Enumerator(bit) >>> req.body )
        case None => Responder( body = Enumerator.eof )
      }
    */

      // Ross wins the challenge
    case req if req.pathInfo == "/challenge" =>
      Iteratee.head[HttpChunk].map {
        case Some(bits) if (new String(bits.bytes)).startsWith("Go") =>
          Ok.transform(Enumeratee.heading(Enumerator(bits)))
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
    Iteratee.eofOrElse[B](Responder(ResponsePrelude(status = StatusLine.RequestEntityTooLarge)))(s).map(_.right.map(f))

}