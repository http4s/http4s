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

import cats.Functor
import cats.MonoidK
import cats.data.Kleisli
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.Stream
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
  def apply[F[_]: Functor, G[_]: Concurrent](http: Http[F, G])(implicit F: MonoidK[F]): Http[F, G] =
    Kleisli { req =>
      req.method match {
        case HEAD => http(req) <+> http(req.withMethod(GET)).map(drainBody[G])
        case _ => http(req)
      }
    }

  def httpRoutes[F[_]: Concurrent](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(httpRoutes)

  private[this] def drainBody[G[_]: Concurrent](response: Response[G]): Response[G] =
    response.copy(body = response.body.interruptWhen[G](Stream(true)).drain)
}
