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
package server.middleware

import cats.Functor
import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.syntax.all._
import org.http4s.headers.`Cache-Control`

/** Simple middleware for adding a static set of headers to responses
  * returned by a kleisli.
  */
object StaticHeaders {
  def apply[F[_]: Functor, G[_], A](
      headers: Headers
  )(http: Kleisli[F, A, Response[G]]): Kleisli[F, A, Response[G]] =
    Kleisli { req =>
      http(req).map(resp => resp.copy(headers = headers ++ resp.headers))
    }

  private val noCacheHeader = `Cache-Control`(NonEmptyList.of(CacheDirective.`no-cache`()))

  def `no-cache`[F[_]: Functor, G[_], A](
      http: Kleisli[F, A, Response[G]]
  ): Kleisli[F, A, Response[G]] =
    StaticHeaders(Headers(noCacheHeader))(http)
}
