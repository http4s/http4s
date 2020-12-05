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

@deprecated("Replaced by HttpRoutes", "0.19")
object HttpService extends Serializable {

  /** Lifts a total function to an `HttpService`. The function is expected to
    * handle all requests it is given.  If `f` is a `PartialFunction`, use
    * `apply` instead.
    */
  @deprecated("Use liftF with an OptionT[F, Response[F]] instead", "0.18")
  def lift[F[_]: Functor](f: Request[F] => F[Response[F]]): HttpService[F] =
    Kleisli(f.andThen(OptionT.liftF(_)))

  /** Lifts a partial function to [[HttpRoutes]].  Responds with
    * `OptionT.none` for any request where `pf` is not defined.
    *
    * Unlike `HttpRoutes.of`, does not suspend the application of `pf`.
    */
  @deprecated("Replaced by `HttpRoutes.of`", "0.19")
  def apply[F[_]](pf: PartialFunction[Request[F], F[Response[F]]])(implicit
      F: Applicative[F]): HttpRoutes[F] =
    Kleisli(req => pf.andThen(OptionT.liftF(_)).applyOrElse(req, Function.const(OptionT.none)))

  @deprecated("Replaced by `HttpRoutes.empty`", "0.19")
  def empty[F[_]: Applicative]: HttpRoutes[F] =
    HttpRoutes.empty
}
