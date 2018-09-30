package org.http4s
package server
package middleware

import cats.data.Kleisli
import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._
import org.http4s.util.CaseInsensitiveString
import org.log4s._

/**
  * Simple Middleware for Logging Requests As They Are Processed
  */
object RequestLogger {
  private[this] val logger = getLogger

  def apply[F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: String => Unit = logger.info(_)
  )(@deprecatedName('service) httpApp: HttpApp[F])(
      implicit F: Concurrent[F]
  ): HttpApp[F] =
    Kleisli { req =>
      if (!logBody) {
        Logger
          .logMessage[F, Request[F]](req)(logHeaders, logBody)(logAction) *> httpApp(req)
      } else {
        Ref[F]
          .of(Vector.empty[Chunk[Byte]])
          .flatMap { vec =>
            val newBody = Stream
              .eval(vec.get)
              .flatMap(v => Stream.emits(v).covary[F])
              .flatMap(c => Stream.chunk(c).covary[F])

            val changedRequest = req.withBodyStream(
              req.body
              // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                .observe(_.chunks.flatMap(c => Stream.eval_(vec.update(_ :+ c))))
            )
            val response: F[Response[F]] = httpApp(changedRequest)
            response.attempt
              .flatMap {
                case Left(e) =>
                  Logger.logMessage[F, Request[F]](req.withBodyStream(newBody))(
                    logHeaders,
                    logBody,
                    redactHeadersWhen)(logAction) *>
                    F.raiseError[Response[F]](e)
                case Right(resp) =>
                  F.pure(
                    resp.withBodyStream(
                      resp.body.onFinalize(
                        Logger.logMessage[F, Request[F]](req.withBodyStream(newBody))(
                          logHeaders,
                          logBody,
                          redactHeadersWhen)(logAction)
                      )
                    )
                  )
              }
          }
      }
    }

}
