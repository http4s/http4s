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

import cats.data.{Kleisli, OptionT}
import cats.{Applicative, Defer}
import cats.syntax.all._

object ContextRoutes {

  /** Lifts a function into an [[ContextRoutes]].  The application of `run`
    * is suspended in `F` to permit more efficient combination of
    * routes via `SemigroupK`.
    *
    * @tparam F the effect of the [[ContextRoutes]]
    * @tparam T the type of the auth info in the [[ContextRequest]] accepted by the [[ContextRoutes]]
    * @param run the function to lift
    * @return an [[ContextRoutes]] that wraps `run`
    */
  def apply[T, F[_]](run: ContextRequest[F, T] => OptionT[F, Response[F]])(implicit
      F: Defer[F]): ContextRoutes[T, F] =
    Kleisli(req => OptionT(F.defer(run(req).value)))

  /** Lifts a partial function into an [[ContextRoutes]].  The application of the
    * partial function is suspended in `F` to permit more efficient combination
    * of authed services via `SemigroupK`.
    *
    * @tparam F the base effect of the [[ContextRoutes]]
    * @param pf the partial function to lift
    * @return An [[ContextRoutes]] that returns some [[Response]] in an `OptionT[F, *]`
    * wherever `pf` is defined, an `OptionT.none` wherever it is not
    */
  def of[T, F[_]](pf: PartialFunction[ContextRequest[F, T], F[Response[F]]])(implicit
      F: Defer[F],
      FA: Applicative[F]): ContextRoutes[T, F] =
    Kleisli(req => OptionT(F.defer(pf.lift(req).sequence)))

  /** Lifts a partial function into an [[ContextRoutes]].  The application of the
    * partial function is not suspended in `F`, unlike [[of]]. This allows for less
    * constraints when not combining many routes.
    *
    * @tparam F the base effect of the [[ContextRoutes]]
    * @param pf the partial function to lift
    * @return A [[ContextRoutes]] that returns some [[Response]] in an `OptionT[F, *]`
    * wherever `pf` is defined, an `OptionT.none` wherever it is not
    */
  def strict[T, F[_]: Applicative](
      pf: PartialFunction[ContextRequest[F, T], F[Response[F]]]): ContextRoutes[T, F] =
    Kleisli(req => OptionT(pf.lift(req).sequence))

  /** The empty service (all requests fallthrough).
    *
    * @tparam T - ignored.
    * @return
    */
  def empty[T, F[_]: Applicative]: ContextRoutes[T, F] =
    Kleisli.liftF(OptionT.none)
}
