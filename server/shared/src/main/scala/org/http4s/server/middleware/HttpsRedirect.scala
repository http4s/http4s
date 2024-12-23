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

import cats.Applicative
import cats.Monad
import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.data.OptionT
import cats.syntax.all._
import org.http4s.Status.MovedPermanently
import org.http4s.Uri.Authority
import org.http4s.Uri.RegName
import org.http4s.Uri.Scheme
import org.http4s.headers.Host
import org.http4s.headers.Location
import org.http4s.headers.`Content-Type`
import org.http4s.syntax.header._
import org.typelevel.ci._
import org.typelevel.log4cats
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.LoggerFactoryGen

/** [[Middleware]] to redirect http traffic to https.
  * Inspects `X-Forwarded-Proto` header and if it is set to `http`,
  * redirects to `Host` with same URL with https schema; otherwise does nothing.
  * This middleware is useful when a service is deployed behind a load balancer
  * which does not support such redirect feature, e.g. Heroku.
  */
object HttpsRedirect {
  def apply[F[_]: LoggerFactoryGen, G[_]](http: Http[F, G])(implicit F: Applicative[F]): Http[F, G] = {
    implicit val logger: log4cats.Logger[F] = LoggerFactory.getLogger[F]
    Kleisli { req =>
      (req.headers.get(ci"X-Forwarded-Proto"), req.headers.get[Host]) match {
        case (Some(NonEmptyList(proto, _)), Some(host))
            if Scheme.fromString(proto.value).contains(Scheme.http) =>
          logger.debug(s"Redirecting ${req.method} ${req.uri} to https on $host").as {
            val authority = Authority(host = RegName(host.value))
            val location = req.uri.copy(scheme = Some(Scheme.https), authority = Some(authority))
            val headers = Headers(Location(location), `Content-Type`(MediaType.text.xml))
            Response[G](status = MovedPermanently, headers = headers)
          }
        case _ =>
          http(req)
      }
    }
  }

  def httpRoutes[F[_]: Monad: LoggerFactoryGen](httpRoutes: HttpRoutes[F]): HttpRoutes[F] = {
    implicit val factory: LoggerFactoryGen[OptionT[F, *]] =
      implicitly[LoggerFactoryGen[F]].mapK(OptionT.liftK)
    apply(httpRoutes)
  }

  def httpApp[F[_]: Applicative: LoggerFactoryGen](httpApp: HttpApp[F]): HttpApp[F] =
    apply(httpApp)
}
