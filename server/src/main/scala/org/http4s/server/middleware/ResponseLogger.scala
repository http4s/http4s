package org.http4s
package server
package middleware

import cats.data.Kleisli
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

  def apply[F[_], A](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None)(
      @deprecatedName('service) http: Kleisli[F, A, Response[F]])(
      implicit F: Concurrent[F]
  ): Kleisli[F, A, Response[F]] = {
    val fallback : String => F[Unit] = s => Sync[F].delay(logger.info(s))
    val log = logAction.fold(fallback)(identity)
    Kleisli[F, A, Response[F]] { req =>
      http(req)
        .flatMap { response =>
          if (!logBody)
            Logger.logMessage[F, Response[F]](response)(logHeaders, logBody, redactHeadersWhen)(
              log) *> F.delay(response)
          else
            Ref[F].of(Vector.empty[Chunk[Byte]]).map { vec =>
              val newBody = Stream
                .eval(vec.get)
                .flatMap(v => Stream.emits(v).covary[F])
                .flatMap(c => Stream.chunk(c).covary[F])

              response.copy(
                body = response.body
                // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                  .observe(_.chunks.flatMap(c => Stream.eval_(vec.update(_ :+ c))))
                  .onFinalize {
                    Logger.logMessage[F, Response[F]](response.withBodyStream(newBody))(
                      logHeaders,
                      logBody,
                      redactHeadersWhen)(log)
                  }
              )
            }
        }
        .handleErrorWith(t =>
          F.delay(log(s"service raised an error: ${t.getClass}")) *> F
            .raiseError[Response[F]](t))
    }
  }
}
