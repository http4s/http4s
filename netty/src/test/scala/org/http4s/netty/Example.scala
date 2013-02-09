package org.http4s
package netty

import play.api.libs.iteratee.{Concurrent, Done}
import org.http4s._
import Bodies._
import com.typesafe.scalalogging.slf4j.Logging

object Example extends App with Logging {

  SimpleNettyServer {

    case req if req.pathInfo == "/ping" =>
      logger.info("Got a ping request")
      Done(Responder(body = "pong"))

    case req if req.pathInfo == "/stream" =>
      Done(Responder(body = Concurrent unicast { channel =>
          for (i <- 1 to 10) {
            channel.push(s"$i".getBytes)
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      }))

    case req if req.pathInfo == "/echo" =>
      Done(Responder(body = req.body))
  }

}