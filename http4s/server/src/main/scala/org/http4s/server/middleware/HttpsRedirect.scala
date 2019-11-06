package org.http4s
package server
package middleware

import cats.Applicative
import cats.implicits._
import cats.data.Kleisli
import org.http4s.headers.{ `Content-Type`, `X-Forwarded-Proto`, Host, Location }
import org.http4s._
import org.http4s.dsl.io.MovedPermanently
import org.http4s.util.CaseInsensitiveString
import org.http4s.Uri.{ Authority, Host, RegName, Scheme }

import org.log4s.getLogger

/**
  * [[Middleware]] to redirect http traffic to https.
  * Inspects `X-Forwarded-Proto` header and if it is set to `http`,
  * redirects to `Host` with same URL with https schema; otherwise does nothing.
  * This middleware is useful when a service is deployed behind a load balancer
  * which does not support such redirect feature, e.g. Heroku.
  */
object HttpsRedirect {

  private[HttpsRedirect] val logger = getLogger

  def apply[F[_], G[_]](http: Http[F, G])(implicit F: Applicative[F]): Http[F, G] =
    Kleisli { req =>
      (req.headers.get(`X-Forwarded-Proto`), req.headers.get(Host)) match {
        
        case (Some(proto), Some(host)) if Scheme.fromString(proto.value).contains(Scheme.http) =>
          logger.debug(s"Redirecting ${req.method} ${req.uri} to https on $host")
          val authority = Authority(host = RegName(host.value))
          val location  = req.uri.copy(scheme = Some(Scheme.https), authority = Some(authority))
          val headers   = Headers(Location(location) :: `Content-Type`(MediaType.text.xml) :: Nil)
          val response  = Response[G](status = MovedPermanently, headers = headers)
          response.pure[F]
        
        case _ =>
          http(req)
      }
    }

}
