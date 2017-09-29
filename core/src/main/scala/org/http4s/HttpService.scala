package org.http4s

import cats._
import cats.data._
import org.http4s.syntax.kleisli._

object HttpService extends Serializable {

  /**
    * Lifts a total function to an `HttpService`. The function is expected to
    * handle all requests it is given.  If `f` is a `PartialFunction`, use
    * `apply` instead.
    */
  def lift[F[_]: Functor](f: Request[F] => F[Response[F]]): HttpService[F] =
    Kleisli(f).liftOptionT

  def liftF[F[_]](f: Request[F] => OptionT[F, Response[F]]): HttpService[F] =
    Kleisli(f)

  /** Lifts a partial function to an `HttpService`. Responds with
    * [[org.http4s.Response.notFoundFor]], which generates a 404, for any request
    * where `pf` is not defined.
    */
  def apply[F[_]](pf: PartialFunction[Request[F], F[Response[F]]])(
      implicit F: Applicative[F]): HttpService[F] =
    Kleisli(req => pf.andThen(OptionT.liftF(_)).applyOrElse(req, Function.const(OptionT.none)))

  def empty[F[_]: Applicative]: HttpService[F] =
    Kleisli.lift(OptionT.none)

}
