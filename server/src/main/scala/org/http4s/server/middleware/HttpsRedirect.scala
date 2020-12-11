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

import cats.{Applicative, Monad}
import cats.syntax.all._
import cats.data.Kleisli
import org.http4s.Status.MovedPermanently
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s.headers.{Host, Location, `Content-Type`, `X-Forwarded-Proto`}

import org.log4s.getLogger

/** [[Middleware]] to redirect http traffic to https.
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
          val location = req.uri.copy(scheme = Some(Scheme.https), authority = Some(authority))
          val headers = Headers(Location(location) :: `Content-Type`(MediaType.text.xml) :: Nil)
          val response = Response[G](status = MovedPermanently, headers = headers)
          response.pure[F]

        case _ =>
          http(req)
      }
    }

  def httpRoutes[F[_]: Monad](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(httpRoutes)

  def httpApp[F[_]: Applicative](httpApp: HttpApp[F]): HttpApp[F] =
    apply(httpApp)
}
