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

import cats.Functor
import cats.arrow.FunctionK
import cats.data.OptionT
import cats.effect.kernel.Async
import cats.effect.kernel.MonadCancelThrow
import cats.~>
import fs2.Stream
import org.typelevel.ci.CIString

sealed abstract class LoggerBuilder[F[_]] extends internal.Logger[F, LoggerBuilder[F]] {
  def apply[G[_]](fk: F ~> G)(
      http: Http[G, F]
  )(implicit F: Async[F], G: MonadCancelThrow[G]): Http[G, F]

  def apply[G[_]](
      http: Http[G, F]
  )(implicit lift: Logger.Lift[F, G], F: Async[F], G: MonadCancelThrow[G]): Http[G, F] =
    apply(lift.fk)(http)
}

/** Simple Middleware for Logging All Requests and Responses
  */
object Logger {
  private[middleware] final case class Impl[F[_]](
      responseLogger: ResponseLoggerBuilder[F],
      requestLogger: RequestLoggerBuilder[F],
  ) extends LoggerBuilder[F] {

    override def apply[G[_]](fk: F ~> G)(
        http: Http[G, F]
    )(implicit F: Async[F], G: MonadCancelThrow[G]): Http[G, F] =
      responseLogger(fk)(requestLogger(fk)(http))

    override def withRedactHeadersWhen(f: CIString => Boolean): LoggerBuilder[F] =
      copy(
        responseLogger = responseLogger.withRedactHeadersWhen(f),
        requestLogger = requestLogger.withRedactHeadersWhen(f),
      )

    override def withLogAction(f: String => F[Unit]): LoggerBuilder[F] = copy(
      responseLogger = responseLogger.withLogAction(f),
      requestLogger = requestLogger.withLogAction(f),
    )
  }

  def builder[F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
  ): LoggerBuilder[F] =
    Impl(
      ResponseLogger.builder(logHeaders, logBody),
      RequestLogger.builder(logHeaders, logBody),
    )

  def builder[F[_]](
      logHeaders: Boolean,
      renderBodyWith: Stream[F, Byte] => Option[F[String]],
  ): LoggerBuilder[F] = Impl(
    ResponseLogger.builder(logHeaders, renderBodyWith),
    RequestLogger.builder(logHeaders, renderBodyWith),
  )

  @deprecated("Use Logger.builder", "0.23.15")
  def apply[G[_], F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(http: Http[G, F])(implicit G: MonadCancelThrow[G], F: Async[F]): Http[G, F] =
    builder(logHeaders, logBody)
      .withRedactHeadersWhen(redactHeadersWhen)
      .withLogActionOpt(logAction)(fk)(http)

  @deprecated("Use Logger.builder", "0.23.15")
  def logBodyText[G[_], F[_]](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(http: Http[G, F])(implicit G: MonadCancelThrow[G], F: Async[F]): Http[G, F] =
    builder(logHeaders, logBody)
      .withRedactHeadersWhen(redactHeadersWhen)
      .withLogActionOpt(logAction)(fk)(http)

  @deprecated("Use Logger.builder", "0.23.15")
  def httpApp[F[_]: Async](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: HttpApp[F]): HttpApp[F] =
    builder(logHeaders, logBody)
      .withRedactHeadersWhen(redactHeadersWhen)
      .withLogActionOpt(logAction)(httpApp)

  @deprecated("Use Logger.builder", "0.23.15")
  def httpAppLogBodyText[F[_]: Async](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: HttpApp[F]): HttpApp[F] =
    builder(logHeaders, logBody)
      .withRedactHeadersWhen(redactHeadersWhen)
      .withLogActionOpt(logAction)(httpApp)

  @deprecated("Use Logger.builder", "0.23.15")
  def httpRoutes[F[_]: Async](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    builder(logHeaders, logBody)
      .withRedactHeadersWhen(redactHeadersWhen)
      .withLogActionOpt(logAction)(httpRoutes)

  @deprecated("Use Logger.builder", "0.23.15")
  def httpRoutesLogBodyText[F[_]: Async](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    builder(logHeaders, logBody)
      .withRedactHeadersWhen(redactHeadersWhen)
      .withLogActionOpt(logAction)(httpRoutes)

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
