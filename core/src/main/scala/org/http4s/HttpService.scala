package org.http4s

import cats._
import cats.data._

@deprecated("Replaced by HttpRoutes", "0.19")
object HttpService extends Serializable {

  /**
    * Lifts a total function to an `HttpService`. The function is expected to
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
  def apply[F[_]](pf: PartialFunction[Request[F], F[Response[F]]])(
      implicit F: Applicative[F]): HttpRoutes[F] =
    Kleisli(req => pf.andThen(OptionT.liftF(_)).applyOrElse(req, Function.const(OptionT.none)))

  @deprecated("Replaced by `HttpRoutes.empty`", "0.19")
  def empty[F[_]: Applicative]: HttpRoutes[F] =
    HttpRoutes.empty
}
