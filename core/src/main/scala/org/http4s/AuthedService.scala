/*
 * Copyright 2013 http4s.org
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

import cats.{Applicative, Functor}
import cats.data.{Kleisli, OptionT}

@deprecated("Use AuthedRoutes instead", "0.20.2")
object AuthedService {

  /** Lifts a total function to an `AuthedService`. The function is expected to
    * handle all requests it is given.  If `f` is a `PartialFunction`, use
    * `apply` instead.
    */
  @deprecated("Use liftF with an OptionT[F, Response[F]] instead", "0.18")
  def lift[F[_]: Functor, T](f: AuthedRequest[F, T] => F[Response[F]]): AuthedService[T, F] =
    Kleisli(f.andThen(OptionT.liftF(_)))

  /** Lifts a partial function to an `AuthedService`.  Responds with
    * [[org.http4s.Response.notFoundFor]], which generates a 404, for any request
    * where `pf` is not defined.
    */
  @deprecated("Use AuthedRoutes.of instead", "0.20.2")
  def apply[T, F[_]](pf: PartialFunction[AuthedRequest[F, T], F[Response[F]]])(implicit
      F: Applicative[F]): AuthedService[T, F] =
    Kleisli(req => pf.andThen(OptionT.liftF(_)).applyOrElse(req, Function.const(OptionT.none)))

  /** The empty service (all requests fallthrough).
    *
    * @tparam T - ignored.
    * @return
    */
  @deprecated("Use AuthedRoutes.empty instead", "0.20.2")
  def empty[T, F[_]: Applicative]: AuthedService[T, F] =
    Kleisli.liftF(OptionT.none)
}
