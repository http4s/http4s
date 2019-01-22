package org.http4s
package client
package middleware

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
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F])(implicit F: Concurrent[F]): Client[F] = {
    val log = logAction.getOrElse { s: String =>
      Sync[F].delay(logger.info(s))
    }
    Client { req =>
      client.run(req).flatMap { response =>
        if (!logBody)
          Resource.liftF(
            Logger.logMessage[F, Response[F]](response)(logHeaders, logBody, redactHeadersWhen)(
              log(_)) *> F.delay(response))
        else
          Resource.suspend {
            Ref[F].of(Vector.empty[Chunk[Byte]]).map { vec =>
              Resource.make(
                F.pure(
                  response.copy(body = response.body
                  // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                    .observe(_.chunks.flatMap(s => Stream.eval_(vec.update(_ :+ s)))))
                )) { _ =>
                val newBody = Stream
                  .eval(vec.get)
                  .flatMap(v => Stream.emits(v).covary[F])
                  .flatMap(c => Stream.chunk(c).covary[F])

                Logger
                  .logMessage[F, Response[F]](response.withBodyStream(newBody))(
                    logHeaders,
                    logBody,
                    redactHeadersWhen)(log(_))
                  .attempt
                  .flatMap {
                    case Left(t) => F.delay(logger.error(t)("Error logging response body"))
                    case Right(()) => F.unit
                  }
              }
            }
          }
      }
    }
  }
}
