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

import cats._
import cats.syntax.all._
import cats.effect._

import org.http4s._
import org.http4s.headers.{Date => HDate}

/** Date Middleware, adds the Date Header to All Responses generated
  * by the service.
  */
object Date {
  def apply[G[_]: Monad: Clock, F[_], A](k: G[Response[F]]): G[Response[F]] =
    k.flatMap { resp =>
      for {
        date <- resp.headers
          .get[HDate]
          .fold(
            HttpDate.current[G].map(HDate(_))
          )(_.pure[G])
      } yield resp.putHeaders(date)
    }

  def httpRoutes[F[_]: Monad: Clock](routes: HttpRoutes[F]): HttpRoutes[F] =
    apply(routes)

  def httpApp[F[_]: Monad: Clock](app: HttpApp[F]): HttpApp[F] =
    apply(app)
}
