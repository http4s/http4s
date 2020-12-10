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
package client
package middleware

import cats.effect._
import fs2.Stream
import org.http4s.util.CaseInsensitiveString

/** Simple Middleware for Logging All Requests and Responses
  */
object Logger {
  def apply[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F]): Client[F] =
    ResponseLogger.apply(logHeaders, logBody, redactHeadersWhen, logAction)(
      RequestLogger.apply(logHeaders, logBody, redactHeadersWhen, logAction)(
        client
      )
    )

  def logBodyText[F[_]: Concurrent](
      logHeaders: Boolean,
      logBody: Stream[F, Byte] => Option[F[String]],
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
      logAction: Option[String => F[Unit]] = None
  )(client: Client[F]): Client[F] =
    ResponseLogger.logBodyText(logHeaders, logBody, redactHeadersWhen, logAction)(
      RequestLogger.logBodyText(logHeaders, logBody, redactHeadersWhen, logAction)(
        client
      )
    )

  def logMessage[F[_], A <: Message[F]](message: A)(
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains)(
      log: String => F[Unit])(implicit F: Sync[F]): F[Unit] =
    org.http4s.internal.Logger
      .logMessage[F, A](message)(logHeaders, logBody, redactHeadersWhen)(log)
}
