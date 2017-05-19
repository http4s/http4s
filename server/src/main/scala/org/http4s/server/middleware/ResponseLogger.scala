package org.http4s
package server
package middleware

import fs2._
import fs2.interop.cats._
import cats.implicits._
import org.log4s.getLogger

/**
  * Simple Middleware for Logging Responses As They Are Processed
  */
object ResponseLogger {
  private[this] val logger = getLogger

  def apply(logHeaders: Boolean, logBody: Boolean)(service: HttpService): HttpService = Service.lift { req =>
    Stream.eval(service(req))
      .map(_.toOption)
      .unNone
      .through(Logger.messageLogPipe[Response](logHeaders, logBody)(logger))
      .runLast
      .map(_.getOrElse(Pass))
  }
}
