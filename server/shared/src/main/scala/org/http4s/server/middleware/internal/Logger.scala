package org.http4s.server.middleware.internal

import cats.data.Kleisli
import cats.effect.kernel.MonadCancelThrow
import cats.~>
import org.http4s.Response
import org.http4s.server.middleware.Logger.Lift
import org.typelevel.ci.CIString

private[http4s] abstract class Logger[F[_], Self <: Logger[F, Self]] {
  self: Self =>
  def apply[G[_], A](fk: F ~> G)(
      http: Kleisli[G, A, Response[F]]
  )(implicit G: MonadCancelThrow[G]): Kleisli[G, A, Response[F]]

  def apply[G[_], A](
      http: Kleisli[G, A, Response[F]]
  )(implicit lift: Lift[F, G], G: MonadCancelThrow[G]): Kleisli[G, A, Response[F]] =
    apply(lift.fk)(http)

  def withRedactHeadersWhen(f: CIString => Boolean): Self

  def withLogAction(f: String => F[Unit]): Self
  def withLogActionOpt(of: Option[String => F[Unit]]): Self =
    of.fold(this)(withLogAction)
}
