package org.http4s

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee._
import org.http4s.Responder
import org.http4s.Request

/**
 * Centralized scratch pad for various http4s use cases.  Should not ship with final product.
 */
private[http4s] object ExampleRoute {
  def apply(implicit executor: ExecutionContext = ExecutionContext.global): Route = {
    case req if req.pathInfo == "/ping" =>
      Future.successful(Responder(body = Enumerator("pong".getBytes())))

    case req if req.requestMethod == Method.Post && req.pathInfo == "/echo" =>
      Future.successful(Responder(body = req.body))

    case req if req.pathInfo == "/echo2" =>
      Future.successful(Responder(body = req.body &> Enumeratee.map[Chunk](e => e.slice(6, e.length))))

    case req if req.requestMethod == Method.Post && req.pathInfo == "/sum" =>
      stringHandler(req, 16) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Responder[Raw](body = Enumerator(sum.toString.getBytes))
      }

    case req if req.pathInfo == "/stream" =>
      Future.successful(Responder(body = Concurrent.unicast({
        channel =>
          for (i <- 1 to 10) {
            channel.push("%d\n".format(i).getBytes)
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      })))

    case req if req.pathInfo == "/fail" =>
      sys.error("FAIL")
  }

  def stringHandler(req: Request[Raw], maxSize: Int = Integer.MAX_VALUE)(f: String => Responder[Raw]): Future[Responder[Raw]] = {
    val it = Traversable.takeUpTo[Chunk](maxSize)
      .transform(Iteratee.consume[Chunk]().asInstanceOf[Iteratee[Chunk, Chunk]].map {
      bs => new String(bs, req.charset)
    })
      .flatMap(Iteratee.eofOrElse(Responder(statusLine = StatusLine.RequestEntityTooLarge, body = EmptyBody)))
      .map(_.right.map(f).merge)
    req.body.run(it)
  }

}