package org.http4s

import cats._
import cats.implicits._
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

  implicit def contextRequestInstances[F[_]]: NonEmptyTraverse[ContextRequest[F, *]] =
    new NonEmptyTraverse[ContextRequest[F, *]] {
      // Members declared in cats.Foldable
      override def foldLeft[A, B](fa: ContextRequest[F, A], b: B)(f: (B, A) => B): B =
        f(b, fa.context)
      override def foldRight[A, B](fa: ContextRequest[F, A], lb: Eval[B])(
          f: (A, Eval[B]) => Eval[B]): Eval[B] =
        f(fa.context, lb)

      // Members declared in cats.NonEmptyTraverse
      override def nonEmptyTraverse[G[_]: Apply, A, B](fa: ContextRequest[F, A])(
          f: A => G[B]): G[ContextRequest[F, B]] =
        f(fa.context).map(b => ContextRequest(b, fa.req))

      // Members declared in cats.Reducible
      def reduceLeftTo[A, B](fa: ContextRequest[F, A])(f: A => B)(g: (B, A) => B): B =
        f(fa.context)
      def reduceRightTo[A, B](fa: ContextRequest[F, A])(f: A => B)(
          g: (A, Eval[B]) => Eval[B]): Eval[B] =
        Eval.later(f(fa.context))
    }
}
