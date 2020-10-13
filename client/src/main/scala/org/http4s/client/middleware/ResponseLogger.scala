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
import org.typelevel.ci.CIString
import org.log4s.getLogger

/** Simple middleware for logging responses as they are processed
  */
object ResponseLogger {
  private[this] val logger = getLogger

  def apply[F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F])(implicit F: Concurrent[F]): Client[F] =
    impl[F](logHeaders, Left(logBody), redactHeadersWhen, logAction)(client)

  def logBodyText[F[_]](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F])(implicit F: Concurrent[F]): Client[F] =
    impl[F](logHeaders, Right(logBody), redactHeadersWhen, logAction)(client)

  private def impl[F[_]](
      logHeaders: Boolean,
      logBodyText: Either[Boolean, Stream[F, Byte] => Option[F[String]]],
      redactHeadersWhen: CIString => Boolean,
      logAction: Option[String => F[Unit]]
  )(client: Client[F])(implicit F: Concurrent[F]): Client[F] = {
    val log = logAction.getOrElse { (s: String) =>
      Sync[F].delay(logger.info(s))
    }

    def logMessage(resp: Response[F]): F[Unit] =
      logBodyText match {
        case Left(bool) =>
          Logger.logMessage[F, Response[F]](resp)(logHeaders, bool, redactHeadersWhen)(log(_))
        case Right(f) =>
          org.http4s.internal.Logger
            .logMessageWithBodyText[F, Response[F]](resp)(logHeaders, f, redactHeadersWhen)(log(_))
      }

    val logBody: Boolean = logBodyText match {
      case Left(bool) => bool
      case Right(_) => true
    }

    Client { req =>
      client.run(req).flatMap { response =>
        if (!logBody)
          Resource.liftF(logMessage(response) *> F.delay(response))
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
                logMessage(response.withBodyStream(newBody)).attempt
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
