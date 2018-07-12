package org.http4s
package client
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

  def apply[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  )(client: Client[F]): Client[F] =
    client.copy(open = Kleisli {
      req =>
        if (!logBody)
          Logger.logMessage[F, Request[F]](req)(logHeaders, logBody, redactHeadersWhen)(
            logger.info(_)
          ) *> client.open(req)
        else
          Ref[F].of(Vector.empty[Segment[Byte, Unit]]).flatMap { vec =>
            val newBody = Stream
              .eval(vec.get)
              .flatMap(v => Stream.emits(v).covary[F])
              .flatMap(c => Stream.segment(c).covary[F])

            val changedRequest = req.withBodyStream(
              req.body
              // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                .observe(_.segments.flatMap(s => Stream.eval_(vec.update(_ :+ s))))
                .onFinalize(
                  Logger.logMessage[F, Request[F]](req.withBodyStream(newBody))(
                    logHeaders,
                    logBody,
                    redactHeadersWhen)(logger.info(_))
                )
            )

            client.open(changedRequest)
          }
    })
}
