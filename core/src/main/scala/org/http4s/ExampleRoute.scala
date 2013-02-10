package org.http4s

import scala.language.reflectiveCalls
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee._

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

    // Reads the whole body before responding
    case req if req.pathInfo == "/determine_echo1" =>
      println("Doing Read a bit and echo Echo")
      req.body.run( Iteratee.getChunks).map {bytes =>
        Responder( body = Enumerator(bytes:_*))
      }

    // Demonstrate how simple it is to read some and then continue
    case req if req.pathInfo == "/determine_echo2" =>
      println("Doing Read a bit and echo Echo")
      val bit: Future[Option[Chunk]] = req.body.run(Iteratee.head)
      bit.map {
        case Some(bit) => Responder( body = Enumerator(bit) >>> req.body )
        case None => Responder( body = Enumerator.eof )
      }

    case req if req.pathInfo == "/fail" =>
      sys.error("FAIL")

    case req =>
      println(s"Request path: ${req.pathInfo}")
      Future.successful(Responder(body =
        Enumerator(s"${req.pathInfo}\n${req.uri}".getBytes)
      ))
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