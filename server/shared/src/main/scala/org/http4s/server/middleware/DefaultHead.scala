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
import cats.Functor
import cats.Monad
import cats.MonoidK
import cats.data.Kleisli
import cats.effect.kernel.Concurrent
import cats.syntax.all._
import org.http4s.Method.GET
import org.http4s.Method.HEAD

/** Handles HEAD requests as a GET without a body.
  *
  * If the service returns the fallthrough response, the request is resubmitted
  * as a GET.  The resulting response's body is killed, but all headers are
  * preserved.  This is a naive, but correct, implementation of HEAD.  Routes
  * requiring more optimization should implement their own HEAD handler.
  */
object DefaultHead {
  def apply[F[_]: Functor, G[_]: Applicative](
      http: Http[F, G]
  )(implicit F: MonoidK[F]): Http[F, G] =
    Kleisli { req =>
      req.method match {
        case HEAD => http(req) <+> http(req.withMethod(GET)).map(drainBody[G])
        case _ => http(req)
      }
    }

  @deprecated("Use overload with Applicative constraint", "0.23.17")
  def apply[F[_], G[_]](
      http: Http[F, G],
      F: Functor[F],
      G: Concurrent[G],
      M: MonoidK[F],
  ): Http[F, G] =
    apply(http)(F, G, M)

  def httpRoutes[F[_]: Monad](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(httpRoutes)

  @deprecated("Use overload with Monad constraint", "0.23.17")
  def httpRoutes[F[_]](httpRoutes: HttpRoutes[F], F: Concurrent[F]): HttpRoutes[F] = {
    implicit val concurrent = F
    apply(httpRoutes)
  }

  private[this] def drainBody[G[_]](
      response: Response[G]
  )(implicit G: Applicative[G]): Response[G] =
    response.pipeBodyThrough(_.interruptWhen[G](G.pure(Either.unit[Throwable])).drain)
}
