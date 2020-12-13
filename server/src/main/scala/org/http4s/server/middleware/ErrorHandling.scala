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

package org.http4s.server
package middleware

import cats.data.Kleisli
import cats._
import cats.syntax.all._
import org.http4s._

object ErrorHandling {
  def apply[F[_], G[_]](k: Kleisli[F, Request[G], Response[G]])(implicit
      F: MonadError[F, Throwable]): Kleisli[F, Request[G], Response[G]] =
    Kleisli { req =>
      val pf: PartialFunction[Throwable, F[Response[G]]] =
        inDefaultServiceErrorHandler[F, G](F)(req)
      k.run(req).handleErrorWith { e =>
        pf.lift(e) match {
          case Some(resp) => resp
          case None => F.raiseError(e)
        }
      }
    }

  def httpRoutes[F[_]: MonadError[*[_], Throwable]](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(httpRoutes)

  def httpApp[F[_]: MonadError[*[_], Throwable]](httpApp: HttpApp[F]): HttpApp[F] =
    apply(httpApp)
}
