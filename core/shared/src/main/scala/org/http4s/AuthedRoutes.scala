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

import cats.Applicative
import cats.Monad
import cats.data.Kleisli
import cats.data.OptionT
import cats.syntax.all._

object AuthedRoutes {

  /** Lifts a function into an [[AuthedRoutes]].  The application of `run`
    * is suspended in `F` to permit more efficient combination of
    * routes via `SemigroupK`.
    *
    * @tparam F the effect of the [[AuthedRoutes]]
    * @tparam T the type of the auth info in the [[AuthedRequest]] accepted by the [[AuthedRoutes]]
    * @param run the function to lift
    * @return an [[AuthedRoutes]] that wraps `run`
    */
  def apply[T, F[_]](run: AuthedRequest[F, T] => OptionT[F, Response[F]])(implicit
      F: Monad[F]
  ): AuthedRoutes[T, F] =
    Kleisli(req => OptionT(F.unit >> run(req).value))

  /** Lifts a partial function into an [[AuthedRoutes]].  The application of the
    * partial function is suspended in `F` to permit more efficient combination
    * of authed services via `SemigroupK`.
    *
    * @tparam F the base effect of the [[AuthedRoutes]]
    * @param pf the partial function to lift
    * @return An [[AuthedRoutes]] that returns some [[Response]] in an `OptionT[F, *]`
    * wherever `pf` is defined, an `OptionT.none` wherever it is not
    */
  def of[T, F[_]](pf: PartialFunction[AuthedRequest[F, T], F[Response[F]]])(implicit
      FA: Monad[F]
  ): AuthedRoutes[T, F] =
    Kleisli(req => OptionT(FA.unit >> pf.lift(req).sequence))

  /** The empty service (all requests fallthrough).
    *
    * @tparam T - ignored.
    * @return
    */
  def empty[T, F[_]: Applicative]: AuthedRoutes[T, F] =
    Kleisli.liftF(OptionT.none)
}
