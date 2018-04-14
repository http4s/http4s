package org.http4s
package server
package middleware

import cats._
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

  def apply[F[_]: Functor](@deprecatedName('service, "0.19") routes: HttpRoutes[F]): HttpRoutes[F] =
    apply(routes, defaultHSTSPolicy)

  def apply[F[_]: Functor](
      @deprecatedName('service, "0.19") routes: HttpRoutes[F],
      header: `Strict-Transport-Security`): HttpRoutes[F] = Kleisli { req =>
    routes.map(_.putHeaders(header)).apply(req)
  }

  def unsafeFromDuration[F[_]: Functor](
      @deprecatedName('service, "0.19") routes: HttpRoutes[F],
      maxAge: FiniteDuration = 365.days,
      includeSubDomains: Boolean = true,
      preload: Boolean = false): HttpRoutes[F] = {
    val header = `Strict-Transport-Security`.unsafeFromDuration(maxAge, includeSubDomains, preload)

    apply(routes, header)
  }

}
