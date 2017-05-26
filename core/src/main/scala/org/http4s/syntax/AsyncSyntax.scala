package org.http4s.syntax

import cats.effect.Async

import scala.concurrent.{ExecutionContext, Future}

trait AsyncSyntax {
  implicit def asyncSyntax[F[_], A](async: Async[F])(implicit ec: ExecutionContext): AsyncOps[F, A] =
    new AsyncOps[F, A](async)
}

final class AsyncOps[F[_], A](self: Async[F])(implicit ec: ExecutionContext) {
  def fromFuture(future: Future[A]): F[A] =
    self.async[A](cb => future.onComplete(cb.compose(_.toEither)))
}
