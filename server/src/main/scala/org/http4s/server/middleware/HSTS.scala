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

  def apply(service: HttpService, maxAge: FiniteDuration = 365.days, includeSubDomains: Boolean = true, preload: Boolean = false): HttpService = Service.lift { req =>

    val header = `Strict-Transport-Security`(maxAge, includeSubDomains, preload)

    service.map {
      case resp: Response =>
        resp.putHeaders(header)
      case Pass           => Pass
    }.apply(req)
  }
}
