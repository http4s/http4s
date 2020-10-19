/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package middleware

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._
import org.log4s.getLogger
import org.typelevel.ci.CIString

/** Simple Middleware for Logging Requests As They Are Processed
  */
object RequestLogger {
  private[this] val logger = getLogger

  def apply[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F]): Client[F] =
    impl[F](logHeaders, Left(logBody), redactHeadersWhen, logAction)(client)

  def logBodyText[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F]): Client[F] =
    impl[F](logHeaders, Right(logBody), redactHeadersWhen, logAction)(client)

  private def impl[F[_]: Concurrent](
      logHeaders: Boolean,
      logBodyText: Either[Boolean, Stream[F, Byte] => Option[F[String]]],
      redactHeadersWhen: CIString => Boolean,
      logAction: Option[String => F[Unit]]
  )(client: Client[F]): Client[F] = {
    val log = logAction.getOrElse { (s: String) =>
      Sync[F].delay(logger.info(s))
    }

    def logMessage(r: Request[F]): F[Unit] =
      logBodyText match {
        case Left(bool) =>
          Logger.logMessage[F, Request[F]](r)(logHeaders, bool, redactHeadersWhen)(log(_))
        case Right(f) =>
          org.http4s.internal.Logger
            .logMessageWithBodyText[F, Request[F]](r)(logHeaders, f, redactHeadersWhen)(log(_))
      }

    val logBody: Boolean = logBodyText match {
      case Left(bool) => bool
      case Right(_) => true
    }

    Client { req =>
      if (!logBody)
        Resource.liftF(logMessage(req)) *> client
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
                .onFinalizeWeak(
                  logMessage(req.withBodyStream(newBody))
                )
            )

            client.run(changedRequest)
          }
        }
    }
  }

}
