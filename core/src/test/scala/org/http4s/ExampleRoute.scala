package org.http4s

import scala.language.reflectiveCalls
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee._

import Bodies._

object ExampleRoute {
  def apply(implicit executor: ExecutionContext = ExecutionContext.global): Route = {
    case req if req.pathInfo == "/ping" =>
      Future.successful(Responder(body = "pong"))

    case req if req.requestMethod == Method.Post && req.pathInfo == "/echo" =>
      Future.successful(Responder(body = req.body))

    case req if req.pathInfo == "/echo" =>
      Future.successful(Responder(body = req.body))

    case req if req.pathInfo == "/echo2" =>
      Future.successful(Responder(body = (req.body &> Enumeratee.map[Raw](e => HttpEntity(e.slice(6, e.length)))) ))

    case req if req.requestMethod == Method.Post && req.pathInfo == "/sum" =>
      stringHandler(req, 16) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Responder(body = sum.toString)
      }

    case req if req.pathInfo == "/stream" =>
      Future.successful(Responder(body = Concurrent.unicast({
        channel =>
          for (i <- 1 to 10) {
            channel.push(HttpEntity("%d\n".format(i).getBytes))
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      })))

    case req if req.pathInfo == "/bigstring" =>
      Future.successful{
        val builder = new StringBuilder(20*1028)

        Responder( body =Enumerator(((0 until 1000) map { i =>
          HttpEntity(s"This is string number $i".getBytes)
        }): _*) )
      }

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

    case req if req.pathInfo == "/fail" =>
      sys.error("FAIL")
 }

  def stringHandler(req: Request[Raw], maxSize: Int = Integer.MAX_VALUE)(f: String => Responder[HttpChunk]): Future[Responder[HttpChunk]] = {
    val it: Iteratee[Raw,Responder[HttpChunk]] = (
                Traversable.takeUpTo[Raw](maxSize)
                transform bytesAsString(req)
                flatMap eofOrRequestTooLarge(f)
                map (_.merge)
      )
    req.body.run(it)
  }

  private[this] def bytesAsString(req: Request[Raw]) =
    Iteratee.consume[Raw]().asInstanceOf[Iteratee[Raw, Raw]].map(new String(_, req.charset))

  private[this] def eofOrRequestTooLarge[B](f: String => Responder[HttpChunk])(s: String) =
    Iteratee.eofOrElse[B](Responder(statusLine = StatusLine.RequestEntityTooLarge, body = EmptyBody))(s).map(_.right.map(f))

}