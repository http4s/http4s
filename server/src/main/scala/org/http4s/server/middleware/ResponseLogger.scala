package org.http4s
package server
package middleware

import cats._
import cats.arrow.FunctionK
import cats.data._
import cats.effect._
import cats.effect.implicits._
import cats.effect.Sync._
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

  def apply[G[_], F[_], A](
      logHeaders: Boolean,
      logBody: Boolean,
      fk: F ~> G,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None)(
      @deprecatedName('service) http: Kleisli[G, A, Response[F]])(
      implicit G: Bracket[G, Throwable],
      F: Concurrent[F]): Kleisli[G, A, Response[F]] = {
    val fallback: String => F[Unit] = s => Sync[F].delay(logger.info(s))
    val log = logAction.fold(fallback)(identity)
    Kleisli[G, A, Response[F]] { req =>
      http(req)
        .flatMap { response =>
          val out =
            if (!logBody)
              Logger
                .logMessage[F, Response[F]](response)(logHeaders, logBody, redactHeadersWhen)(log)
                .as(response)
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
          fk(out)
        }
        .guaranteeCase {
          case ExitCase.Error(t) => fk(log(s"service raised an error: ${t.getClass}"))
          case ExitCase.Canceled => fk(log(s"service cancelled response for request [$req]"))
          case ExitCase.Completed => G.unit
        }
    }
  }

  def httpApp[F[_]: Concurrent, A](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None)(
      httpApp: Kleisli[F, A, Response[F]]): Kleisli[F, A, Response[F]] =
    apply(logHeaders, logBody, FunctionK.id[F], redactHeadersWhen, logAction)(httpApp)

  def httpRoutes[F[_]: Concurrent, A](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None)(
      httpRoutes: Kleisli[OptionT[F, ?], A, Response[F]]): Kleisli[OptionT[F, ?], A, Response[F]] =
    apply(logHeaders, logBody, OptionT.liftK[F], redactHeadersWhen, logAction)(httpRoutes)
}
