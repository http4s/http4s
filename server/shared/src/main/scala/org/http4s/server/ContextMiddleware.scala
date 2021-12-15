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

import cats.Monad
import cats.data.Kleisli
import cats.data.OptionT
import cats.syntax.all._

object ContextMiddleware {
  def apply[F[_]: Monad, T](
      getContext: Kleisli[OptionT[F, *], Request[F], T]
  ): ContextMiddleware[F, T] =
    _.compose(Kleisli((r: Request[F]) => getContext(r).map(ContextRequest(_, r))))

  /** Useful for Testing, Construct a Middleware from a single
    * value T to use as the context
    *
    * @param t The value to use as the context
    * @return A ContextMiddleware that always provides T
    */
  def const[F[_]: Monad, T](t: T): ContextMiddleware[F, T] =
    apply(Kleisli(_ => t.pure[OptionT[F, *]]))
}
