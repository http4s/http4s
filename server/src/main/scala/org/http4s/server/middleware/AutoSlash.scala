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

import cats.{Monad, MonoidK}
import cats.data.{Kleisli, OptionT}
import cats.mtl.Local
import cats.syntax.all._

/** Removes a trailing slash from [[Request]] path
  *
  * If a route exists with a file style [[Uri]], eg "/foo",
  * this middleware will cause [[Request]]s with uri = "/foo" and
  * uri = "/foo/" to match the route.
  */
object AutoSlash {
  def apply[F[_], G[_], B](fb: F[B])(implicit
      F: Monad[F],
      FMK: MonoidK[F],
      L: Local[F, Request[G]]
  ): F[B] =
    fb <+> L.ask.flatMap { req =>
      val pathInfo = req.pathInfo
      if (pathInfo.isEmpty)
        FMK.empty
      else
        L.local(fb)(_.withPathInfo(pathInfo.dropEndsWithSlash))
    }

  /** Convenience constructor to help type inference in Scala 2.12. */
  def httpRoutes[F[_]: Monad](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply[Kleisli[OptionT[F, *], Request[F], *], F, Response[F]](httpRoutes)
}
