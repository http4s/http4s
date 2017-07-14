package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import fs2._
import org.log4s.getLogger

/**
  * Simple Middleware for Logging Responses As They Are Processed
  */
object ResponseLogger {
  private[this] val logger = getLogger

  def apply[F[_]: Effect](logHeaders: Boolean, logBody: Boolean)
                         (service: HttpService[F]): HttpService[F] = Service.lift { req =>
    Stream.eval(service(req))
      .map(_.toOption)
      .unNone
      .through(Logger.messageLogPipe[F, Response[F]](logHeaders, logBody)(logger))
      .runLast
      .map(_.getOrElse(Pass()))
  }
}
