package org.http4s
package server
package middleware

import cats.effect._
import fs2._
import org.log4s._

/**
  * Simple Middleware for Logging Requests As They Are Processed
  */
object RequestLogger {
  private[this] val logger = getLogger

  def apply[F[_]: Effect](logHeaders: Boolean, logBody: Boolean)
                         (service: HttpService[F]): HttpService[F] =
    Service.lift{ req: Request[F] =>
      Stream(req)
        .covary[F]
        .through(Logger.messageLogPipe[F, Request[F]](logHeaders, logBody)(logger))
        .evalMap[MaybeResponse[F]]{req => service(req)}
        .runFoldMonoid
    }
}
