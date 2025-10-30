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

import cats.effect.kernel.Async
import org.typelevel.ci.CIString

/** Configuration for logging actions in server middleware.
  *
  * @param logHeaders Whether to log headers
  * @param logBody Whether to log the body
  * @param redactHeadersWhen Function to determine which headers should be redacted
  * @param logAction The effectful log action that returns a logging function
  */
final case class LogAction[F[_]] private[middleware] (
    logHeaders: Boolean,
    logBody: Boolean,
    redactHeadersWhen: CIString => Boolean,
    logAction: F[String => F[Unit]],
)

object LogAction {
  private[this] val logger = Platform.loggerFactory.getLogger

  /** Creates a builder for LogAction.
    */
  def default[F[_]: Async]: LogActionBuilder[F] =
    new LogActionBuilder[F](
      logHeaders = false,
      logBody = false,
      redactHeadersWhen = Logger.defaultRedactHeadersWhen,
      logAction = Async[F].pure(s => logger.info(s).to[F]),
    )

  final case class LogActionBuilder[F[_]: Async] private[LogAction] (
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean,
      logAction: F[String => F[Unit]],
  ) {

    /** Sets whether to log headers.
      *
      * @param logHeaders
      */
    def withLogHeaders(logHeaders: Boolean): LogActionBuilder[F] =
      copy(logHeaders = logHeaders)

    /** Sets whether to log body.
      * @param logBody
      */
    def withLogBody(logBody: Boolean): LogActionBuilder[F] =
      copy(logBody = logBody)

    /** Sets the function to determine which headers should be redacted.
      *
      * @param redactHeadersWhen function to determine which headers to redact
      */
    def withRedactHeadersWhen(redactHeadersWhen: CIString => Boolean): LogActionBuilder[F] =
      copy(redactHeadersWhen = redactHeadersWhen)

    /** Sets the log action function.
      *
      * @param f log action
      */
    def withLogAction(f: String => F[Unit]): LogActionBuilder[F] =
      copy(logAction = Async[F].pure(f))

    /** Sets a deferred log action function.
      *
      * Use this when you want loggerFactory to capture context e.g. via IOLocal
      * at the time of logging. Otherwise, context may be lost.
      * Important for cases where `logBody` is enabled, which may happen on
      * another fiber.
      *
      * @example  {{{
      * import cats.effect.IOLocal
      * import org.typelevel.log4cats.Logger
      * val ioLocal = IOLocal(Map.empty[String, String])
      * val logger: Logger[IO] = ...
      * val logF: IO[String => IO[Unit]] =
      *   ioLocal.get.map { ctx =>
      *     { s => logger.info(s"$ctx $s") }
      *   }
      *
      * LogAction.default[IO]
      *   .withLogBody(true)
      *   .withDeferredLogAction(logF)
      *   .build
      * }}}
      *
      * @param ff deferred log action
      */
    def withDeferredLogAction(ff: F[String => F[Unit]]): LogActionBuilder[F] =
      copy(logAction = ff)

    def build: LogAction[F] =
      LogAction(
        logHeaders = logHeaders,
        logBody = logBody,
        redactHeadersWhen = redactHeadersWhen,
        logAction = logAction,
      )
  }
}
