package org.http4s
package server
package middleware

import cats.effect._
import fs2._
import org.log4s._
import scala.concurrent.ExecutionContext

/**
  * Simple Middleware for Logging Requests As They Are Processed
  */
object RequestLogger {
  private[this] val logger = getLogger

  def apply[F[_]: Effect](logHeaders: Boolean, logBody: Boolean)
           (service: HttpService[F])
           (implicit ec: ExecutionContext = ExecutionContext.global): HttpService[F] =

    Service.lift{ req =>
      if (!logBody) {
        Logger.logMessage(req)(logHeaders, logBody)(logger)(strategy) >> service(req)
      } else {
        async.unboundedQueue[Task, Byte].flatMap { queue =>

          val newBody = Stream.eval(queue.size.get)
            .flatMap(size => queue.dequeue.take(size.toLong))

          val changedRequest = req.withBody(
            req.body
              .observe(queue.enqueue)
              .onFinalize(Logger.logMessage(req.withBody(newBody))(logHeaders, logBody)(logger)(strategy))
          )

          service(changedRequest)
        }
      }
  }
}
