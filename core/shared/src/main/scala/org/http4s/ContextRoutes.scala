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
import cats.Monoid
import cats.data.Kleisli
import cats.data.OptionT
import cats.syntax.all._

object ContextRoutes {

  /** Lifts a function into an [[ContextRoutes]].  The application of `run`
    * is suspended in `F` to permit more efficient combination of
    * routes via `SemigroupK`.
    *
    * @tparam F the effect of the [[ContextRoutes]]
    * @tparam T the type of the context info in the [[ContextRequest]] accepted by the [[ContextRoutes]]
    * @param run the function to lift
    * @return an [[ContextRoutes]] that wraps `run`
    */
  def apply[T, F[_]](run: ContextRequest[F, T] => OptionT[F, Response[F]])(implicit
      F: Monad[F]
  ): ContextRoutes[T, F] =
    Kleisli(req => OptionT(F.unit >> run(req).value))

  /** Lifts a partial function into an [[ContextRoutes]].  The application of the
    * partial function is suspended in `F` to permit more efficient combination
    * of authed services via `SemigroupK`.
    *
    * @tparam F the base effect of the [[ContextRoutes]]
    * @tparam T the type of the context info in the [[ContextRequest]] accepted by the [[ContextRoutes]]
    * @param pf the partial function to lift
    * @return An [[ContextRoutes]] that returns some [[Response]] in an `OptionT[F, *]`
    * wherever `pf` is defined, an `OptionT.none` wherever it is not
    */
  def of[T, F[_]](pf: PartialFunction[ContextRequest[F, T], F[Response[F]]])(implicit
      F: Monad[F]
  ): ContextRoutes[T, F] =
    Kleisli(req => OptionT(Applicative[F].unit >> pf.lift(req).sequence))

  /** Lifts a partial function into an [[ContextRoutes]].  The application of the
    * partial function is not suspended in `F`, unlike [[of]]. This allows for less
    * constraints when not combining many routes.
    *
    * @tparam F the base effect of the [[ContextRoutes]]
    * @tparam T the type of the context info in the [[ContextRequest]] accepted by the [[ContextRoutes]]
    * @param pf the partial function to lift
    * @return A [[ContextRoutes]] that returns some [[Response]] in an `OptionT[F, *]`
    * wherever `pf` is defined, an `OptionT.none` wherever it is not
    */
  def strict[T, F[_]: Applicative](
      pf: PartialFunction[ContextRequest[F, T], F[Response[F]]]
  ): ContextRoutes[T, F] =
    Kleisli(req => OptionT(pf.lift(req).sequence))

  /** The empty service (all requests fallthrough).
    *
    * @tparam T - ignored.
    * @return
    */
  def empty[T, F[_]: Applicative]: ContextRoutes[T, F] =
    Kleisli.liftF(OptionT.none)

  /** Lifts an effectful [[Response]] into an [[ContextRoutes]].
    *
    * @tparam F the effect of the [[ContextRoutes]]
    * @param fr the effectful [[Response]] to lift
    * @return an [[ContextRoutes]] that always returns `fr`
    */
  def liftF[T, F[_]](fr: OptionT[F, Response[F]]): ContextRoutes[T, F] =
    Kleisli.liftF(fr)

  /** Lifts a [[Response]] into an [[ContextRoutes]].
    *
    * @tparam F the base effect of the [[ContextRoutes]]
    * @tparam T the type of the context info in the [[ContextRequest]] accepted by the [[ContextRoutes]]
    * @param r the [[Response]] to lift
    * @return an [[ContextRoutes]] that always returns `r` in effect `OptionT[F, *]`
    */
  def pure[T, F[_]](r: Response[F])(implicit FO: Applicative[OptionT[F, *]]): ContextRoutes[T, F] =
    Kleisli.pure(r)

  /** Transforms an [[ContextRequest]] on its input.  The application of the
    * transformed function is suspended in `F` to permit more
    * efficient combination of routes via `SemigroupK`.
    *
    * @tparam F the base effect of the [[ContextRoutes]]
    * @tparam T the type of the context info in the [[ContextRequest]] accepted by the [[ContextRoutes]]
    * @param f  a function to apply to the [[ContextRequest]]
    * @param fa the [[ContextRoutes]] to transform
    * @return An [[ContextRoutes]] whose input is transformed by `f` before
    *         being applied to `fa`
    */
  def local[T, F[_]: Monad](f: ContextRequest[F, T] => ContextRequest[F, T])(
      fa: ContextRoutes[T, F]
  ): ContextRoutes[T, F] = Kleisli(req => Monad[OptionT[F, *]].unit >> fa.run(f(req)))

  /** Converts a [[ContextRoutes]] to [[HttpRoutes]]. It uses a `Monoid.empty` to supply a value for the emptyContext.
    * The application of `routes` is suspended in `F` to permit more efficient combination of
    * routes via `SemigroupK`.
    *
    * @tparam F the effect of the [[ContextRoutes]]
    * @tparam T the type of the context info in the [[ContextRequest]] accepted by the [[ContextRoutes]]
    * @param routes the [[ContextRoutes]] to transform
    * @return an [[ContextRoutes]] that wraps `run`
    */
  def toHttpRoutes[T: Monoid, F[_]](routes: ContextRoutes[T, F]): HttpRoutes[F] =
    toHttpRoutes(Monoid[T].empty)(routes)

  /** Converts a [[ContextRoutes]] to [[HttpRoutes]].
    * The application of `routes` is suspended in `F` to permit more efficient combination of
    * routes via `SemigroupK`.
    *
    * @tparam F the effect of the [[ContextRoutes]]
    * @tparam T the type of the context info in the [[ContextRequest]] accepted by the [[ContextRoutes]].
    * @param emptyContext the empty context
    * @param routes the [[ContextRoutes]] to transform
    * @return an [[ContextRoutes]] that wraps `run`
    */
  def toHttpRoutes[T, F[_]](emptyContext: T)(routes: ContextRoutes[T, F]): HttpRoutes[F] =
    Kleisli(req => routes.run(ContextRequest(emptyContext, req)))
}
