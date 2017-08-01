package org.http4s
package server
package middleware

import fs2._
import org.log4s._
import cats.implicits._
import fs2.interop.cats._
import scodec.bits._

/**
  * Simple Middleware for Logging Requests As They Are Processed
  */
object RequestLogger {
  private[this] val logger = getLogger

  def apply(logHeaders: Boolean, logBody: Boolean)(service: HttpService)(implicit strategy: Strategy): HttpService =

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
