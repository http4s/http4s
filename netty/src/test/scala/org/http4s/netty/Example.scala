package org.http4s
package netty

import play.api.libs.iteratee.{Enumeratee, Concurrent, Done}
import org.http4s._
import Bodies._
import com.typesafe.scalalogging.slf4j.Logging
import concurrent.Future

object Example extends App with Logging {

  import concurrent.ExecutionContext.Implicits.global

  SimpleNettyServer() {

    case req if req.pathInfo == "/ping" =>
      logger.info("Got a ping request")
      Future.successful(Responder(body = "pong"))

    case req if req.pathInfo == "/stream" =>
      Future.successful(Responder(body = Concurrent unicast { channel =>
          for (i <- 1 to 10) {
            channel.push(s"$i".getBytes)
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      }))

    case req if req.pathInfo == "/echo2" =>
      Future.successful(Responder(body = req.body &> Enumeratee.map[Chunk](e => e.slice(6, e.length))))

    case req if req.pathInfo == "/echo" =>
      println("In the route")
      Future.successful(Responder(body = req.body))
  }

}