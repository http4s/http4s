/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import cats.~>
import cats.arrow.FunctionK
import cats.data.{Kleisli, OptionT}
import cats.effect.kernel.{Async, MonadCancel, Outcome, Sync}
import cats.effect.syntax.all._
import cats.implicits._
import fs2.{Chunk, Stream}
import org.log4s.getLogger
import org.typelevel.ci.CIString

/** Simple middleware for logging responses as they are processed
  */
object ResponseLogger {
  private[this] val logger = getLogger

  def apply[G[_], F[_], A](
      logHeaders: Boolean,
      logBody: Boolean,
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None)(http: Kleisli[G, A, Response[F]])(implicit
      G: MonadCancel[G, Throwable],
      F: Async[F]): Kleisli[G, A, Response[F]] =
    impl[G, F, A](logHeaders, Left(logBody), fk, redactHeadersWhen, logAction)(http)

  private[server] def impl[G[_], F[_], A](
      logHeaders: Boolean,
      logBodyText: Either[Boolean, Stream[F, Byte] => Option[F[String]]],
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean,
      logAction: Option[String => F[Unit]])(http: Kleisli[G, A, Response[F]])(implicit
      G: MonadCancel[G, Throwable],
      F: Async[F]): Kleisli[G, A, Response[F]] = {
    val fallback: String => F[Unit] = s => Sync[F].delay(logger.info(s))
    val log = logAction.fold(fallback)(identity)

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

    Kleisli[G, A, Response[F]] { req =>
      http(req)
        .flatMap { response =>
          val out =
            if (!logBody)
              logMessage(response)
                .as(response)
            else
              F.ref(Vector.empty[Chunk[Byte]]).map { vec =>
                val newBody = Stream
                  .eval(vec.get)
                  .flatMap(v => Stream.emits(v).covary[F])
                  .flatMap(c => Stream.chunk(c).covary[F])

                response.copy(
                  body = response.body
                    // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                    .observe(_.chunks.flatMap(c => Stream.exec(vec.update(_ :+ c))))
                    .onFinalizeWeak {
                      logMessage(response.withBodyStream(newBody))
                    }
                )
              }
          fk(out)
        }
        .guaranteeCase { (oc: Outcome[G, _, Response[F]]) =>
          oc match {
            case Outcome.Errored(t) => fk(log(s"service raised an error: ${t.getClass}"))
            case Outcome.Canceled() => fk(log(s"service canceled response for request"))
            case Outcome.Succeeded(_) => G.unit
          }
        }
    }
  }

  def httpApp[F[_]: Async, A](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None)(
      httpApp: Kleisli[F, A, Response[F]]): Kleisli[F, A, Response[F]] =
    apply(logHeaders, logBody, FunctionK.id[F], redactHeadersWhen, logAction)(httpApp)

  def httpAppLogBodyText[F[_]: Async, A](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None)(
      httpApp: Kleisli[F, A, Response[F]]): Kleisli[F, A, Response[F]] =
    impl[F, F, A](logHeaders, Right(logBody), FunctionK.id[F], redactHeadersWhen, logAction)(
      httpApp)

  def httpRoutes[F[_]: Async, A](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None)(
      httpRoutes: Kleisli[OptionT[F, *], A, Response[F]]): Kleisli[OptionT[F, *], A, Response[F]] =
    apply(logHeaders, logBody, OptionT.liftK[F], redactHeadersWhen, logAction)(httpRoutes)

  def httpRoutesLogBodyText[F[_]: Async, A](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None)(
      httpRoutes: Kleisli[OptionT[F, *], A, Response[F]]): Kleisli[OptionT[F, *], A, Response[F]] =
    impl[OptionT[F, *], F, A](
      logHeaders,
      Right(logBody),
      OptionT.liftK[F],
      redactHeadersWhen,
      logAction)(httpRoutes)
}
