/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats.Functor
import cats.data.{Kleisli, NonEmptyList}
import cats.syntax.all._
import org.http4s.{CacheDirective, Header, Headers, Response}
import org.http4s.headers.`Cache-Control`

/**
  * Simple middleware for adding a static set of headers to responses
  * returned by a kleisli.
  */
object StaticHeaders {
  def apply[F[_]: Functor, G[_], A](headers: Headers)(
      http: Kleisli[F, A, Response[G]]): Kleisli[F, A, Response[G]] =
    Kleisli { req =>
      http(req).map(resp => resp.copy(headers = headers ++ resp.headers))
    }

  private val noCacheHeader: Header = `Cache-Control`(NonEmptyList.of(CacheDirective.`no-cache`()))

  def `no-cache`[F[_]: Functor, G[_], A](
      http: Kleisli[F, A, Response[G]]): Kleisli[F, A, Response[G]] =
    StaticHeaders(Headers(noCacheHeader.pure[List]))(http)
}
