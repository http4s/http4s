package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import fs2._
import org.log4s.getLogger
import scala.concurrent.ExecutionContext

/**
  * Simple Middleware for Logging Responses As They Are Processed
  */
object ResponseLogger {
  private[this] val logger = getLogger

  def apply[F[_]](logHeaders: Boolean, logBody: Boolean)
           (service: HttpService[F])
           (implicit ec: ExecutionContext = ExecutionContext.global): HttpService[F] =
    Service.lift { req => service(req).flatMap {
        case Pass => Pass.now
        case response: Response =>
          if (!logBody) {
            Logger.logMessage(response)(logHeaders, logBody)(logger)(strategy) >> Task(response)
          } else {
            fs2.async.unboundedQueue[Task, Byte].map { queue =>
              val newBody = Stream.eval(queue.size.get)
                .flatMap(size => queue.dequeue.take(size.toLong))

              response.copy(
                body = response.body
                  .observe(queue.enqueue)
                  .onFinalize {
                    Logger.logMessage(response.copy(body = newBody))(logHeaders, logBody)(logger)(strategy)
                  }
              )
            }
          }
    }}
}
