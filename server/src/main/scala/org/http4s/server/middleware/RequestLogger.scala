package org.http4s
package server
package middleware

import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.util.CaseInsensitiveString
import org.log4s._
import scala.concurrent.ExecutionContext

/**
  * Simple Middleware for Logging Requests As They Are Processed
  */
object RequestLogger {
  private[this] val logger = getLogger

  def apply[F[_]: Effect](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  )(service: HttpService[F])(
      implicit ec: ExecutionContext = ExecutionContext.global): HttpService[F] =
    Kleisli { req =>
      if (!logBody)
        OptionT(
          Logger.logMessage[F, Request[F]](req)(logHeaders, logBody)(logger) *> service(req).value)
      else
        OptionT.liftF(async.refOf[F, Vector[Byte]](Vector.empty[Byte])).flatMap { vec =>
          val newBody =
            Stream
              .eval(vec.get)
              .flatMap(Stream.emits(_).covary[F])

          val changedRequest = req.withBodyStream(
            req.body
              .observe(_.chunks.flatMap(c => Stream.eval_(vec.modify(_ ++ c.toVector))))
              .onFinalize(
                Logger.logMessage[F, Request[F]](req.withBodyStream(newBody))(
                  logHeaders,
                  logBody,
                  redactHeadersWhen)(logger)
              )
          )

          service(changedRequest)
        }
    }
}
