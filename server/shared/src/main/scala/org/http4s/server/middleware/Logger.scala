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
import cats.data.OptionT
import cats.effect.kernel.Async
import cats.effect.kernel.MonadCancelThrow
import cats.syntax.all._
import cats.{Functor, ~>}
import fs2.Stream
import org.log4s.getLogger
import org.typelevel.ci.CIString

/** Simple Middleware for Logging All Requests and Responses
  */
object Logger {
  private[this] val logger = getLogger

  def apply[G[_], F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(http: Http[G, F])(implicit G: MonadCancelThrow[G], F: Async[F]): Http[G, F] = {
    val log: String => F[Unit] = logAction.getOrElse { s =>
      F.delay(logger.info(s))
    }
    ResponseLogger
      .builder(logHeaders, logBody)
      .withRedactHeadersWhen(redactHeadersWhen)
      .withLogAction(log)(fk)(
        RequestLogger
          .builder(logHeaders, logBody)
          .withRedactHeadersWhen(redactHeadersWhen)
          .withLogAction(log)(fk)(http)
      )
  }

  def logBodyText[G[_], F[_]](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(http: Http[G, F])(implicit G: MonadCancelThrow[G], F: Async[F]): Http[G, F] = {
    val log: String => F[Unit] = logAction.getOrElse { s =>
      F.delay(logger.info(s))
    }
    ResponseLogger.impl(logHeaders, Right(logBody), fk, redactHeadersWhen, log.pure[Option])(
      RequestLogger.impl(logHeaders, Right(logBody), fk, redactHeadersWhen, log.pure[Option])(http)
    )
  }

  def httpApp[F[_]: Async](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: HttpApp[F]): HttpApp[F] =
    apply(logHeaders, logBody, FunctionK.id[F], redactHeadersWhen, logAction)(httpApp)

  def httpAppLogBodyText[F[_]: Async](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: HttpApp[F]): HttpApp[F] =
    logBodyText(logHeaders, logBody, FunctionK.id[F], redactHeadersWhen, logAction)(httpApp)

  def httpRoutes[F[_]: Async](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(logHeaders, logBody, OptionT.liftK[F], redactHeadersWhen, logAction)(httpRoutes)

  def httpRoutesLogBodyText[F[_]: Async](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    logBodyText(logHeaders, logBody, OptionT.liftK[F], redactHeadersWhen, logAction)(httpRoutes)

  def logMessage[F[_], A <: Message[F]](message: A)(
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
  )(log: String => F[Unit])(implicit F: Async[F]): F[Unit] =
    org.http4s.internal.Logger
      .logMessage(message)(logHeaders, logBody, redactHeadersWhen)(log)

  /** A type class representing the ability to lift one type constructor to another */
  sealed abstract class Lift[F[_], G[_]] {
    def fk: F ~> G
  }

  object Lift {
    implicit def liftId[F[_]]: Lift[F, F] = new Lift[F, F] {
      def fk: F ~> F = FunctionK.id[F]
    }

    implicit def liftOptionT[F[_]: Functor]: Lift[F, OptionT[F, *]] = new Lift[F, OptionT[F, *]] {
      override def fk: F ~> OptionT[F, *] = OptionT.liftK[F]
    }
  }
}
