package org.http4s

import cats.data.{Kleisli, OptionT}
import cats.{Applicative, Defer}
import cats.implicits._

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
  def apply[T, F[_]](run: ContextRequest[F, T] => OptionT[F, Response[F]])(
      implicit F: Defer[F]): ContextRoutes[T, F] =
    Kleisli(req => OptionT(F.defer(run(req).value)))

  /** Lifts a partial function into an [[ContextRoutes]].  The application of the
    * partial function is suspended in `F` to permit more efficient combination
    * of authed services via `SemigroupK`.
    *
    * @tparam F the base effect of the [[ContextRoutes]]
    * @param pf the partial function to lift
    * @return An [[ContextRoutes]] that returns some [[Response]] in an `OptionT[F, ?]`
    * wherever `pf` is defined, an `OptionT.none` wherever it is not
    */
  def of[T, F[_]](pf: PartialFunction[ContextRequest[F, T], F[Response[F]]])(
      implicit F: Defer[F], FA: Applicative[F]): ContextRoutes[T, F] =
    Kleisli(req => OptionT(F.defer(pf.lift(req).sequence)))

  /**
    * The empty service (all requests fallthrough).
    *
    * @tparam T - ignored.
    * @return
    */
  def empty[T, F[_]: Applicative]: ContextRoutes[T, F] =
    Kleisli.liftF(OptionT.none)

}
