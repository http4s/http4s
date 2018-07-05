package org.http4s.server.middleware

import cats.Functor
import cats.data.{Kleisli, NonEmptyList}
import org.http4s.{CacheDirective, Header, Headers, HttpService}
import org.http4s.headers.`Cache-Control`

/**
  * Simple Middleware for adding a static set of headers to responses returned by the service.
  */
object StaticHeaders {

  def apply[F[_]: Functor](headers: Headers)(service: HttpService[F]): HttpService[F] = 
    Kleisli { req =>
      service(req).map(resp => resp.copy(headers = headers ++ resp.headers))
    }

  private val noCacheHeader: Header = `Cache-Control`(NonEmptyList.of(CacheDirective.`no-cache`()))
  def `no-cache`[F[_]: Functor](service: HttpService[F]): HttpService[F] = StaticHeaders[F](Headers(noCacheHeader))(service)
}