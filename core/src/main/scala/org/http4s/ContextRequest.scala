package org.http4s

import cats.syntax.functor._
import cats.{Functor, ~>}
import cats.data.Kleisli

final case class ContextRequest[F[_], A](context: A, req: Request[F]) {
  def mapK[G[_]](fk: F ~> G): ContextRequest[G, A] =
    ContextRequest(context, req.mapK(fk))

  @deprecated("Use context instead", "0.21.0")
  def authInfo: A = context

}

object ContextRequest {
  def apply[F[_]: Functor, T](
      getContext: Request[F] => F[T]): Kleisli[F, Request[F], ContextRequest[F, T]] =
    Kleisli(request => getContext(request).map(ctx => ContextRequest(ctx, request)))

  implicit def contextRequestInstances[F[_]]: Functor[ContextRequest[F, *]] =
    new Functor[ContextRequest[F, *]] {
      override def map[A, B](fa: ContextRequest[F, A])(f: A => B): ContextRequest[F, B] =
        ContextRequest(f(fa.context), fa.req)
    }
}
