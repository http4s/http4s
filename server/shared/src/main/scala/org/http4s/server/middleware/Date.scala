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

import cats.data.Kleisli
import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.headers.{Date => HDate}

/** Date Middleware, adds the Date Header to All Responses generated
  * by the service.
  */
object Date {
  def apply[G[_]: Temporal, F[_], A](k: Kleisli[G, A, Response[F]]): Kleisli[G, A, Response[F]] =
    Kleisli(a => k(a).flatMap(addDate[G, F]))

  private[this] def addDate[G[_]: Temporal, F[_]](resp: Response[F]): G[Response[F]] =
    resp.headers.get[HDate] match {
      case None => HttpDate.current[G].map(d => resp.putHeaders(HDate(d)))
      case Some(_) => resp.pure[G]
    }

  def httpRoutes[F[_]: Temporal](routes: HttpRoutes[F]): HttpRoutes[F] =
    apply(routes)

  def httpApp[F[_]: Temporal](app: HttpApp[F]): HttpApp[F] =
    apply(app)
}
