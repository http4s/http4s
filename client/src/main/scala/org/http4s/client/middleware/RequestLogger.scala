package org.http4s
package client
package middleware

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

  def apply[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F]): Client[F] = {
    val log = logAction.getOrElse { s: String =>
      Sync[F].delay(logger.info(s))
    }
    Client { req =>
      if (!logBody)
        Resource.liftF(Logger
          .logMessage[F, Request[F]](req)(logHeaders, logBody, redactHeadersWhen)(log(_))) *> client
          .run(req)
      else
        Resource.suspend {
          Ref[F].of(Vector.empty[Chunk[Byte]]).map { vec =>
            val newBody = Stream
              .eval(vec.get)
              .flatMap(v => Stream.emits(v).covary[F])
              .flatMap(c => Stream.chunk(c).covary[F])

            val changedRequest = req.withBodyStream(
              req.body
              // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                .observe(_.chunks.flatMap(s => Stream.eval_(vec.update(_ :+ s))))
                .onFinalize(
                  Logger.logMessage[F, Request[F]](req.withBodyStream(newBody))(
                    logHeaders,
                    logBody,
                    redactHeadersWhen)(log(_))
                )
            )

            client.run(changedRequest)
          }
        }
    }
  }
}
