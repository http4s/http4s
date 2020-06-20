/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats.Functor
import cats.data.Kleisli
import org.http4s._
import cats.implicits._
import org.http4s.util.CaseInsensitiveString

object HeaderEcho {

  /**
    * Simple server middleware that adds selected headers present on the request to the response.
    *
    * @param echoHeadersWhen the function that selects which headers to echo on the response
    * @param http [[Http]] to transform
    */
  def apply[F[_]: Functor, G[_]](echoHeadersWhen: CaseInsensitiveString => Boolean)(
      http: Http[F, G]): Http[F, G] =
    Kleisli { (req: Request[G]) =>
      val headersToEcho = req.headers.filter(h => echoHeadersWhen(h.name))

      http(req).map(_.putHeaders(headersToEcho.toList: _*))
    }

  def httpRoutes[F[_]: Functor](echoHeadersWhen: CaseInsensitiveString => Boolean)(
      httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(echoHeadersWhen)(httpRoutes)

  def httpApp[F[_]: Functor](echoHeadersWhen: CaseInsensitiveString => Boolean)(
      httpApp: HttpApp[F]): HttpApp[F] =
    apply(echoHeadersWhen)(httpApp)
}
