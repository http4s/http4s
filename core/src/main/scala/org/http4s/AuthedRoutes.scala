/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.{Applicative, Defer}
import cats.data.{Kleisli, OptionT}
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
      F: Defer[F]): AuthedRoutes[T, F] =
    Kleisli(req => OptionT(F.defer(run(req).value)))

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
      F: Defer[F],
      FA: Applicative[F]): AuthedRoutes[T, F] =
    Kleisli(req => OptionT(F.defer(pf.lift(req).sequence)))

  /** The empty service (all requests fallthrough).
    *
    * @tparam T - ignored.
    * @return
    */
  def empty[T, F[_]: Applicative]: AuthedRoutes[T, F] =
    Kleisli.liftF(OptionT.none)
}
