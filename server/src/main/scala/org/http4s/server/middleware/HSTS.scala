/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import cats.Functor
import cats.data.Kleisli
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

  def apply[F[_]: Functor, A, G[_]: Functor](
      routes: Kleisli[F, A, Response[G]]): Kleisli[F, A, Response[G]] =
    apply(routes, defaultHSTSPolicy)

  def apply[F[_]: Functor, A, G[_]: Functor](
      http: Kleisli[F, A, Response[G]],
      header: `Strict-Transport-Security`): Kleisli[F, A, Response[G]] =
    Kleisli { req =>
      http.map(_.putHeaders(header)).apply(req)
    }

  def unsafeFromDuration[F[_]: Functor, A, G[_]: Functor](
      http: Kleisli[F, A, Response[G]],
      maxAge: FiniteDuration = 365.days,
      includeSubDomains: Boolean = true,
      preload: Boolean = false): Kleisli[F, A, Response[G]] = {
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
