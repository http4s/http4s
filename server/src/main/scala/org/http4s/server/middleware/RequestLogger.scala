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

  def apply(logHeaders: Boolean, logBody: Boolean)(service: HttpService)(implicit strategy: Strategy): HttpService = Service.lift{ req =>
    Stream(req)
      .through(Logger.messageLogPipe[Request](logHeaders, logBody)(logger))
      .evalMap[Task, Task, MaybeResponse]{req => service(req)}
      .runFoldMap(identity)
  }
}
