package org.http4s
package server
package middleware

import cats._
import cats.arrow.FunctionK
import cats.data._
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

  def apply[G[_], F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      fk: F ~> G,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(@deprecatedName('service) http: Http[G, F])(
      implicit F: Concurrent[F],
      G: MonadError[G, Throwable]
  ): Http[G, F] = {
    val log = logAction.fold({ s: String =>
      Sync[F].delay(logger.info(s))
    })(identity)
    Kleisli { req =>
      if (!logBody) {
        http(req) <* fk(
          // Log Occurs at Response Header Time
          Logger.logMessage[F, Request[F]](req)(logHeaders, logBody)(log)
        )

      } else {
        fk(Ref[F].of(Vector.empty[Chunk[Byte]]))
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
            val response: G[Response[F]] = http(changedRequest)
            response.attempt
              .flatMap {
                case Left(e) =>
                  fk(
                    Logger.logMessage[F, Request[F]](req.withBodyStream(newBody))(
                      logHeaders,
                      logBody,
                      redactHeadersWhen)(log) *>
                      F.raiseError[Response[F]](e)
                  )
                case Right(resp) =>
                  G.pure(
                    resp.withBodyStream(
                      resp.body.onFinalize(
                        Logger.logMessage[F, Request[F]](req.withBodyStream(newBody))(
                          logHeaders,
                          logBody,
                          redactHeadersWhen)(log)
                      )
                    )
                  )
              }
          }
      }
    }
  }

  def httpApp[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(httpApp: HttpApp[F]): HttpApp[F] =
    apply(logHeaders, logBody, FunctionK.id[F], redactHeadersWhen, logAction)(httpApp)

  def httpRoutes[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(logHeaders, logBody, OptionT.liftK[F], redactHeadersWhen, logAction)(httpRoutes)

}
