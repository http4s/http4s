package org.http4s

import scala.language.reflectiveCalls
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee._

object ExampleRoute {
  import Writable._

  def apply(implicit executor: ExecutionContext = ExecutionContext.global): Route = {
    case req if req.pathInfo == "/ping" =>
      Done(Responder()("pong"))

    case req if req.requestMethod == Method.Post && req.pathInfo == "/echo" =>
      Done(Responder(body = Enumeratee.passAlong))

    case req if req.pathInfo == "/echo" =>
      Done(Responder(body = Enumeratee.passAlong))

/*
    case req if req.pathInfo == "/echo2" =>
      Future.successful(Responder(body = req.body &> Enumeratee.map[Chunk](e => e.slice(6, e.length))))
*/

    case req if req.requestMethod == Method.Post && req.pathInfo == "/sum" =>
      stringHandler(req, 16) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Responder()(sum)
      }

    case req if req.pathInfo == "/stream" =>
      Done(Responder().feed(Concurrent.unicast[Chunk]({
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

        Responder().feed(Enumerator.enumerate((0 until 1000) map { i => s"This is string number $i" }))
      }

/*
>>>>>>> Responder as an enumeratee makes a stateless request.
    // Reads the whole body before responding
    case req if req.pathInfo == "/determine_echo1" =>
      req.body.run( Iteratee.getChunks).map {bytes =>
        Responder( body = Enumerator(bytes:_*))
      }

    // Demonstrate how simple it is to read some and then continue
    case req if req.pathInfo == "/determine_echo2" =>
      val bit: Future[Option[Chunk]] = req.body.run(Iteratee.head)
      bit.map {
        case Some(bit) => Responder( body = Enumerator(bit) >>> req.body )
        case None => Responder( body = Enumerator.eof )
      }
*/

    // Challenge
    case req if req.pathInfo == "/challenge" =>
      val bits: Future[Option[Raw]] = req.body.run(Iteratee.head)
      bits.map {
        case Some(bits) if (new String(bits)).startsWith("Go") => Responder( body = Enumerator(bits) >>> req.body )
        case Some(bits) if (new String(bits)).startsWith("NoGo") => Responder( statusLine = StatusLine.BadRequest, body = "Booo!" )
        case _ => Responder( statusLine = StatusLine.BadRequest, body = "No data!" )
      }

    case req if req.pathInfo == "/fail" =>
      sys.error("FAIL")
 }

  def stringHandler(req: RequestHead, maxSize: Int = Integer.MAX_VALUE)(f: String => Responder): Iteratee[Chunk, Responder] = {
    val it = (Traversable.takeUpTo[Chunk](maxSize)
                transform bytesAsString(req)
                flatMap eofOrRequestTooLarge(f)
                map (_.merge))
    it
  }

  private[this] def bytesAsString(req: RequestHead) =
    Iteratee.consume[Chunk]().asInstanceOf[Iteratee[Chunk, Chunk]].map(new String(_, req.charset))

  private[this] def eofOrRequestTooLarge[B](f: String => Responder)(s: String) =
    Iteratee.eofOrElse[B](Responder(statusLine = StatusLine.RequestEntityTooLarge))(s).map(_.right.map(f))

}