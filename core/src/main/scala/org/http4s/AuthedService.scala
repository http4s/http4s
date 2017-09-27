package org.http4s

import cats.{Applicative, Functor}
import cats.data.{Kleisli, OptionT}

object AuthedService {

  /**
    * Lifts a total function to an `AuthedService`. The function is expected to
    * handle all requests it is given.  If `f` is a `PartialFunction`, use
    * `apply` instead.
    */
  def lift[F[_]: Functor, T](f: AuthedRequest[F, T] => F[Response[F]]): AuthedService[F, T] =
    Kleisli(f).transform(FToOptionT)

  def liftF[F[_], T](f: AuthedRequest[F, T] => OptionT[F, Response[F]]): AuthedService[F, T] =
    Kleisli(f)

  /** Lifts a partial function to an `AuthedService`.  Responds with
    * [[org.http4s.Response.notFoundFor]], which generates a 404, for any request
    * where `pf` is not defined.
    */
  def apply[F[_], T](pf: PartialFunction[AuthedRequest[F, T], F[Response[F]]])(
      implicit F: Applicative[F]): AuthedService[F, T] =
    Kleisli(req => pf.andThen(OptionT.liftF(_)).applyOrElse(req, Function.const(OptionT.none)))

  /**
    * The empty service (all requests fallthrough).
    *
    * @tparam T - ignored.
    * @return
    */
  def empty[F[_]: Applicative, T]: AuthedService[F, T] =
    Kleisli.lift(OptionT.none)

}
