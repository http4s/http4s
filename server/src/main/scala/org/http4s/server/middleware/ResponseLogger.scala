package org.http4s
package server
package middleware

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.util.CaseInsensitiveString
import org.log4s.getLogger
import scala.concurrent.ExecutionContext

/**
  * Simple middleware for logging responses as they are processed
  */
object ResponseLogger {
  private[this] val logger = getLogger

  def apply[F[_]: Effect, A](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: String => Unit = logger.info(_))(
      @deprecatedName('service) http: Kleisli[F, A, Response[F]])(
      implicit ec: ExecutionContext = ExecutionContext.global,
      F: Sync[F]): Kleisli[F, A, Response[F]] =
    Kleisli[F, A, Response[F]] { req =>
      http(req)
        .flatMap { response =>
          if (!logBody)
            Logger.logMessage[F, Response[F]](response)(logHeaders, logBody, redactHeadersWhen)(
              logAction) *> F.delay(response)
          else
            async.refOf[F, Vector[Segment[Byte, Unit]]](Vector.empty[Segment[Byte, Unit]]).map {
              vec =>
                val newBody = Stream
                  .eval(vec.get)
                  .flatMap(v => Stream.emits(v).covary[F])
                  .flatMap(c => Stream.segment(c).covary[F])

                response.copy(
                  body = response.body
                  // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                    .observe(_.segments.flatMap(s => Stream.eval_(vec.modify(_ :+ s))))
                    .onFinalize {
                      Logger.logMessage[F, Response[F]](response.withBodyStream(newBody))(
                        logHeaders,
                        logBody,
                        redactHeadersWhen)(logAction)
                    }
                )
            }
        }
        .handleErrorWith(t =>
          F.delay(logger.info(s"service raised an error: ${t.getClass}")) *> F
            .raiseError[Response[F]](t))
    }

}
