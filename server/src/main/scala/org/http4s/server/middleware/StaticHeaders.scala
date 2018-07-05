package org.http4s.server.middleware

import cats.Functor
import cats.implicits._
import cats.data.{Kleisli, NonEmptyList}
import org.http4s.{CacheDirective, Header, Headers, Response}
import org.http4s.headers.`Cache-Control`

/**
  * Simple Middleware for adding a static set of headers to responses returned by the service.
  */
object StaticHeaders {

  def apply[F[_]: Functor, G[_], A](headers: Headers)(@deprecatedName('service) http: Kleisli[F, A, Response[G]]): Kleisli[F, A, Response[G]] =
    Kleisli { req =>
      http(req).map(resp => resp.copy(headers = headers ++ resp.headers))
    }

  private val noCacheHeader: Header = `Cache-Control`(NonEmptyList.of(CacheDirective.`no-cache`()))
  def `no-cache`[F[_]: Functor, G[_], A](@deprecatedName('service) http: Kleisli[F, A, Response[G]]): Kleisli[F, A, Response[G]] =
    StaticHeaders[F, G, A](Headers(noCacheHeader))(http)
}
