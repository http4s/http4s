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

package org.http4s.server.middleware

import cats.Functor
import cats.data.Kleisli
import cats.syntax.all._
import org.http4s._
import org.typelevel.ci.CIString

object HeaderEcho {

  /** Simple server middleware that adds selected headers present on the request to the response.
    *
    * @param echoHeadersWhen the function that selects which headers to echo on the response
    * @param http [[Http]] to transform
    */
  def apply[F[_]: Functor, G[_]](
      echoHeadersWhen: CIString => Boolean
  )(http: Http[F, G]): Http[F, G] =
    Kleisli { (req: Request[G]) =>
      val headersToEcho = Headers(req.headers.headers.filter(h => echoHeadersWhen(h.name)))

      http(req).map(_.putHeaders(headersToEcho.headers))
    }

  def httpRoutes[F[_]: Functor](echoHeadersWhen: CIString => Boolean)(
      httpRoutes: HttpRoutes[F]
  ): HttpRoutes[F] =
    apply(echoHeadersWhen)(httpRoutes)

  def httpApp[F[_]: Functor](echoHeadersWhen: CIString => Boolean)(
      httpApp: HttpApp[F]
  ): HttpApp[F] =
    apply(echoHeadersWhen)(httpApp)
}
