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
import cats.syntax.all._
import org.http4s.headers.`Strict-Transport-Security`
import scala.concurrent.duration._

/** [[Middleware]] to add HTTP Strict Transport Security (HSTS) support adding
  * the Strict Transport Security headers
  */
object HSTS {
  // Default HSTS policy of waiting for 1 year and include sub domains
  private val defaultHSTSPolicy = `Strict-Transport-Security`.unsafeFromDuration(
    365.days,
    includeSubDomains = true,
    preload = false)

  def apply[F[_]: Functor, G[_], A](http: F[Response[G]]): F[Response[G]] =
    apply(http, defaultHSTSPolicy)

  def apply[F[_]: Functor, G[_], A](
      http: F[Response[G]],
      header: `Strict-Transport-Security`): F[Response[G]] =
    http.map(_.putHeaders(header))

  def unsafeFromDuration[F[_]: Functor, G[_], A](
      http: F[Response[G]],
      maxAge: FiniteDuration = 365.days,
      includeSubDomains: Boolean = true,
      preload: Boolean = false): F[Response[G]] = {
    val header = `Strict-Transport-Security`.unsafeFromDuration(maxAge, includeSubDomains, preload)
    apply(http, header)
  }

  object httpRoutes {
    def apply[F[_]: Functor](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
      HSTS.apply(httpRoutes)

    def apply[F[_]: Functor](
        httpRoutes: HttpRoutes[F],
        header: `Strict-Transport-Security`): HttpRoutes[F] =
      HSTS.apply(httpRoutes, header)

    def unsafeFromDuration[F[_]: Functor](
        httpRoutes: HttpRoutes[F],
        maxAge: FiniteDuration = 365.days,
        includeSubDomains: Boolean = true,
        preload: Boolean = false): HttpRoutes[F] =
      HSTS.unsafeFromDuration(httpRoutes, maxAge, includeSubDomains, preload)

  }

  object httpApp {
    def apply[F[_]: Functor](httpApp: HttpApp[F]): HttpApp[F] =
      HSTS.apply(httpApp)

    def apply[F[_]: Functor](httpApp: HttpApp[F], header: `Strict-Transport-Security`): HttpApp[F] =
      HSTS.apply(httpApp, header)

    def unsafeFromDuration[F[_]: Functor](
        httpApp: HttpApp[F],
        maxAge: FiniteDuration = 365.days,
        includeSubDomains: Boolean = true,
        preload: Boolean = false): HttpApp[F] =
      HSTS.unsafeFromDuration(httpApp, maxAge, includeSubDomains, preload)
  }
}
