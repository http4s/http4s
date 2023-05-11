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
import cats.~>
import fs2.Stream
import org.typelevel.ci.CIString

/** Simple Middleware for Logging All Requests and Responses
  */
object Logger {
  private[this] val logger = Platform.loggerFactory.getLogger

  def defaultRedactHeadersWhen(name: CIString): Boolean =
    Headers.SensitiveHeaders.contains(name) || name.toString.toLowerCase.contains("token")

  def apply[G[_], F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean = defaultRedactHeadersWhen,
      logAction: Option[String => F[Unit]] = None,
  )(http: Http[G, F])(implicit G: MonadCancelThrow[G], F: Async[F]): Http[G, F] = {
    val log: String => F[Unit] = logAction.getOrElse { s =>
      logger.info(s).to[F]
    }
    ResponseLogger(logHeaders, logBody, fk, redactHeadersWhen, log.pure[Option])(
      RequestLogger(logHeaders, logBody, fk, redactHeadersWhen, log.pure[Option])(http)
    )
  }

  def logWithEntity[G[_], F[_]](
      logHeaders: Boolean,
      logEntity: Entity[F] => Option[F[String]],
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean = defaultRedactHeadersWhen,
      logAction: Option[String => F[Unit]] = None,
  )(http: Http[G, F])(implicit G: MonadCancelThrow[G], F: Async[F]): Http[G, F] = {
    val log: String => F[Unit] = logAction.getOrElse { s =>
      logger.info(s).to[F]
    }
    ResponseLogger.impl(logHeaders, Right(logEntity), fk, redactHeadersWhen, log.pure[Option])(
      RequestLogger.impl(logHeaders, Right(logEntity), fk, redactHeadersWhen, log.pure[Option])(
        http
      )
    )
  }

  @deprecated(
    "Use Logger.logWithEntity that utilizes Entity model for a Message body",
    "1.0.0-M39",
  )
  def logBodyText[G[_], F[_]](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      fk: F ~> G,
      redactHeadersWhen: CIString => Boolean = defaultRedactHeadersWhen,
      logAction: Option[String => F[Unit]] = None,
  )(http: Http[G, F])(implicit G: MonadCancelThrow[G], F: Async[F]): Http[G, F] =
    logWithEntity[G, F](
      logHeaders,
      (entity: Entity[F]) => logBody(entity.body),
      fk,
      redactHeadersWhen,
      logAction,
    )(http)

  def httpApp[F[_]: Async](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = defaultRedactHeadersWhen,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: HttpApp[F]): HttpApp[F] =
    apply(logHeaders, logBody, FunctionK.id[F], redactHeadersWhen, logAction)(httpApp)

  def httpAppLogEntity[F[_]: Async](
      logHeaders: Boolean,
      logEntity: Entity[F] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = defaultRedactHeadersWhen,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: HttpApp[F]): HttpApp[F] =
    logWithEntity(logHeaders, logEntity, FunctionK.id[F], redactHeadersWhen, logAction)(httpApp)

  @deprecated(
    "Use Logger.httpAppLogEntity that utilizes Entity model for a Message body",
    "1.0.0-M39",
  )
  def httpAppLogBodyText[F[_]: Async](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = defaultRedactHeadersWhen,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: HttpApp[F]): HttpApp[F] =
    httpAppLogEntity(
      logHeaders,
      (entity: Entity[F]) => logBody(entity.body),
      redactHeadersWhen,
      logAction,
    )(httpApp)

  def httpRoutes[F[_]: Async](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = defaultRedactHeadersWhen,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(logHeaders, logBody, OptionT.liftK[F], redactHeadersWhen, logAction)(httpRoutes)

  def httpRoutesLogEntity[F[_]: Async](
      logHeaders: Boolean,
      logEntity: Entity[F] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = defaultRedactHeadersWhen,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    logWithEntity(logHeaders, logEntity, OptionT.liftK[F], redactHeadersWhen, logAction)(httpRoutes)

  @deprecated(
    "Use Logger.httpRoutesLogEntity that utilizes Entity model for a Message body",
    "1.0.0-M39",
  )
  def httpRoutesLogBodyText[F[_]: Async](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CIString => Boolean = defaultRedactHeadersWhen,
      logAction: Option[String => F[Unit]] = None,
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    httpRoutesLogEntity(
      logHeaders,
      (entity: Entity[F]) => logBody(entity.body),
      redactHeadersWhen,
      logAction,
    )(httpRoutes)

  def logMessage[F[_], A <: Message[F]](message: A)(
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = defaultRedactHeadersWhen,
  )(log: String => F[Unit])(implicit F: Async[F]): F[Unit] =
    org.http4s.internal.Logger
      .logMessage(message)(logHeaders, logBody, redactHeadersWhen)(log)
}
