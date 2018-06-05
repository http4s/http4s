package org.http4s
package server
package middleware

import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._
import org.http4s.util.CaseInsensitiveString
import org.log4s.getLogger

/**
  * Simple middleware for logging responses as they are processed
  */
object ResponseLogger {
  private[this] val logger = getLogger

  def apply[F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  )(@deprecatedName('service) routes: HttpRoutes[F])(implicit F: Concurrent[F]): HttpRoutes[F] =
    Kleisli { req =>
      routes(req)
        .semiflatMap { response =>
          if (!logBody)
            Logger.logMessage[F, Response[F]](response)(logHeaders, logBody, redactHeadersWhen)(
              logger.info(_)) *> F.delay(response)
          else
            Ref[F].of(Vector.empty[Segment[Byte, Unit]]).map { vec =>
              val newBody = Stream
                .eval(vec.get)
                .flatMap(v => Stream.emits(v).covary[F])
                .flatMap(c => Stream.segment(c).covary[F])

              response.copy(
                body = response.body
                // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                  .observe(_.segments.flatMap(s => Stream.eval_(vec.update(_ :+ s))))
                  .onFinalize {
                    Logger.logMessage[F, Response[F]](response.withBodyStream(newBody))(
                      logHeaders,
                      logBody,
                      redactHeadersWhen)(logger.info(_))
                  }
              )
            }
        }
        .handleErrorWith(t =>
          OptionT.liftF(F.delay(logger.info(s"service raised an error: ${t.getClass}")) *> F
            .raiseError[Response[F]](t)))
    }
}
