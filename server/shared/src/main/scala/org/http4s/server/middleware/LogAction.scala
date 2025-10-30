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

/** Configuration for logging actions in HTTP middleware.
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
    def withLogHeaders(logHeaders: Boolean): LogActionBuilder[F] =
      copy(logHeaders = logHeaders)

    def withLogBody(logBody: Boolean): LogActionBuilder[F] =
      copy(logBody = logBody)

    def withRedactHeadersWhen(redactHeadersWhen: CIString => Boolean): LogActionBuilder[F] =
      copy(redactHeadersWhen = redactHeadersWhen)

    def withLogAction(f: String => F[Unit]): LogActionBuilder[F] =
      copy(logAction = Async[F].pure(f))

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
