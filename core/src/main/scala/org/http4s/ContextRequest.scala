package org.http4s

import cats.syntax.functor._
import cats.{Functor, ~>}
import cats.data.Kleisli

final case class ContextRequest[F[_], A](context: A, req: Request[F]) {
  def mapK[G[_]](fk: F ~> G): ContextRequest[G, A] =
    ContextRequest(authInfo, req.mapK(fk))

  def authInfo: A = context

}

object ContextRequest {
  def apply[F[_]: Functor, T](
      getContext: Request[F] => F[T]): Kleisli[F, Request[F], ContextRequest[F, T]] =
    Kleisli(request => getContext(request).map(ctx => ContextRequest(ctx, request)))
}
