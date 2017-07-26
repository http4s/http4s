package org.http4s
package server
package middleware

import org.http4s.headers.`Strict-Transport-Security`
import fs2._
import fs2.interop.cats._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/** [[Middleware]] to add HTTP Strict Transport Security (HSTS) support adding
  * the Strict Transport Security headers
  */
object HSTS {
  // Default HSTS policy of waiting for 1 year and include sub domains
  private val defaultHSTSPolicy = `Strict-Transport-Security`.unsafeFromDuration(365.days, includeSubDomains = true, preload = false)

  def apply(service: HttpService): HttpService = apply(service, defaultHSTSPolicy)

  def apply(service: HttpService, header: `Strict-Transport-Security`): HttpService = Service.lift { req =>
    service.map {
      case resp: Response =>
        resp.putHeaders(header)
      case Pass           => Pass
    }.apply(req)
  }

  def unsafeFromDuration(service: HttpService, maxAge: FiniteDuration = 365.days, includeSubDomains: Boolean = true, preload: Boolean = false): HttpService = {
    val header = `Strict-Transport-Security`.unsafeFromDuration(maxAge, includeSubDomains, preload)

    apply(service, header)
  }

}
