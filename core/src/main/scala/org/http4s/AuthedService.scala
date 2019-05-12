package org.http4s

import cats.{Applicative, Functor}
import cats.data.{Kleisli, OptionT}
import cats.implicits._
import cats.effect.Sync

object AuthedService {

  /**
    * Lifts a total function to an `AuthedService`. The function is expected to
    * handle all requests it is given.  If `f` is a `PartialFunction`, use
    * `apply` instead.
    */
  @deprecated("Use liftF with an OptionT[F, Response[F]] instead", "0.18")
  def lift[F[_]: Functor, T](f: AuthedRequest[F, T] => F[Response[F]]): AuthedService[T, F] =
    Kleisli(f.andThen(OptionT.liftF(_)))

  /** Lifts a function into an [[AuthedService]].  The application of `run`
    * is suspended in `F` to permit more efficient combination of
    * routes via `SemigroupK`.
    *
    * @tparam F the effect of the [[AuthedService]]
    * @tparam T the type of the auth info in the [[AuthedRequest]] accepted by the [[AuthedService]]
    * @param run the function to lift
    * @return an [[AuthedService]] that wraps `run`
    */
  def apply_[F[_], T](run: AuthedRequest[F, T] => OptionT[F, Response[F]])(
      implicit F: Sync[F]): AuthedService[T, F] =
    Kleisli(req => OptionT(F.suspend(run(req).value)))

  /** Lifts a partial function to an `AuthedService`.  Responds with
    * [[org.http4s.Response.notFoundFor]], which generates a 404, for any request
    * where `pf` is not defined.
    */
  def apply[T, F[_]](pf: PartialFunction[AuthedRequest[F, T], F[Response[F]]])(
      implicit F: Applicative[F]): AuthedService[T, F] =
    Kleisli(req => pf.andThen(OptionT.liftF(_)).applyOrElse(req, Function.const(OptionT.none)))

  /** Lifts a partial function into an [[AuthedService]].  The application of the
    * partial function is suspended in `F` to permit more efficient combination
    * of authed services via `SemigroupK`.
    *
    * @tparam F the base effect of the [[AuthedService]]
    * @param pf the partial function to lift
    * @return An [[AuthedService]] that returns some [[Response]] in an `OptionT[F, ?]`
    * wherever `pf` is defined, an `OptionT.none` wherever it is not
    */
  def of[F[_], T](pf: PartialFunction[AuthedRequest[F, T], F[Response[F]]])(
      implicit F: Sync[F]): AuthedService[T, F] =
    Kleisli(req => OptionT(F.suspend(pf.lift(req).sequence)))

  /**
    * The empty service (all requests fallthrough).
    *
    * @tparam T - ignored.
    * @return
    */
  def empty[T, F[_]: Applicative]: AuthedService[T, F] =
    Kleisli.liftF(OptionT.none)

}
