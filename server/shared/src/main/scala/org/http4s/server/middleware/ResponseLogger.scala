/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package server
package middleware

import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.kernel.Concurrent
import cats.effect.kernel.MonadCancelThrow
import cats.effect.kernel.Outcome
import cats.effect.syntax.all._
import cats.syntax.all._
import cats.~>
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import org.typelevel.ci.CIString
import org.typelevel.log4cats
import org.typelevel.log4cats.LoggerFactory

/** Simple middleware for logging responses as they are processed
  */
object ResponseLogger {
  def apply[G[_], F[_]: LoggerFactory, A](
      logHeaders: Boolean,
      logBody: Boolean,
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(
      http: Kleisli[G, A, Response[F]]
  )(implicit G: MonadCancelThrow[G], F: Concurrent[F]): Kleisli[G, A, Response[F]] =
    impl[G, F, A](logHeaders, Left(logBody), fk, redactHeadersWhen, logAction)(http)

  private[server] def impl[G[_], F[_]: LoggerFactory, A](
      logHeaders: Boolean,
      logBodyText: Either[Boolean, Stream[F, Byte] => Option[F[String]]],
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean,
      logAction: Option[String => F[Unit]],
  )(
      http: Kleisli[G, A, Response[F]]
  )(implicit G: MonadCancelThrow[G], F: Concurrent[F]): Kleisli[G, A, Response[F]] = {
    implicit val logger: log4cats.Logger[F] = LoggerFactory[F].getLogger
    val errorLogger = LoggerFactory[F].getLoggerFromName("org.http4s.server.service-errors")
    val fallback: String => F[Unit] = s => logger.info(s)
    val log = logAction.fold(fallback)(identity)
    val errLog: (String, Throwable) => F[Unit] = (msg, th) => errorLogger.error(th)(msg)

    def logMessage(resp: Response[F]): F[Unit] =
      logBodyText match {
        case Left(bool) =>
          Logger.logMessage[F, Response[F]](resp)(logHeaders, bool, redactHeadersWhen)(log(_))
        case Right(f) =>
          org.http4s.internal.Logger
            .logMessageWithBodyText(resp)(logHeaders, f, redactHeadersWhen)(log(_))
      }

    val logBody: Boolean = logBodyText match {
      case Left(bool) => bool
      case Right(_) => true
    }

    def logResponse(response: Response[F]): F[Response[F]] =
      if (!logBody)
        logMessage(response)
          .as(response)
      else {
        response.entity match {
          case Entity.Streamed(_, _) =>
            F.ref(Vector.empty[Chunk[Byte]]).map { vec =>
              val newBody = Stream.eval(vec.get).flatMap(v => Stream.emits(v)).unchunks
              // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
              val logPipe: Pipe[F, Byte, Byte] =
                _.observe(_.chunks.flatMap(c => Stream.exec(vec.update(_ :+ c))))
                  .onFinalizeWeak(logMessage(response.withBodyStream(newBody)))

              response.pipeBodyThrough(logPipe)
            }

          case Entity.Strict(_) | Entity.Empty =>
            logMessage(response).as(response)
        }
      }

    Kleisli[G, A, Response[F]] { req =>
      http(req)
        .flatMap((response: Response[F]) => fk(logResponse(response)))
        .guaranteeCase {
          case Outcome.Errored(th) => fk(errLog(s"Service raised an error", th))
          case Outcome.Canceled() => fk(log(s"Service canceled response for request"))
          case Outcome.Succeeded(_) => G.unit
        }
    }
  }

  def httpApp[F[_]: Concurrent: LoggerFactory, A](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: Kleisli[F, A, Response[F]]): Kleisli[F, A, Response[F]] =
    apply(logHeaders, logBody, FunctionK.id[F], redactHeadersWhen, logAction)(httpApp)

  def httpAppLogBodyText[F[_]: Concurrent: LoggerFactory, A](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: Kleisli[F, A, Response[F]]): Kleisli[F, A, Response[F]] =
    impl[F, F, A](logHeaders, Right(logBody), FunctionK.id[F], redactHeadersWhen, logAction)(
      httpApp
    )

  def httpRoutes[F[_]: Concurrent: LoggerFactory, A](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: Kleisli[OptionT[F, *], A, Response[F]]): Kleisli[OptionT[F, *], A, Response[F]] =
    apply(logHeaders, logBody, OptionT.liftK[F], redactHeadersWhen, logAction)(httpRoutes)

  def httpRoutesLogBodyText[F[_]: Concurrent: LoggerFactory, A](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: Kleisli[OptionT[F, *], A, Response[F]]): Kleisli[OptionT[F, *], A, Response[F]] =
    impl[OptionT[F, *], F, A](
      logHeaders,
      Right(logBody),
      OptionT.liftK[F],
      redactHeadersWhen,
      logAction,
    )(httpRoutes)
}
