package org.http4s.syntax

import cats.Eval
import cats.effect.Async

import scala.concurrent.{ExecutionContext, Future}

trait AsyncSyntax {
  implicit def asyncSyntax[F[_], A](async: Async[F])(implicit ec: ExecutionContext): AsyncOps[F, A] =
    new AsyncOps[F, A](async)
}

final class AsyncOps[F[_], A](self: Async[F])(implicit ec: ExecutionContext) {
  def fromFuture(future: Eval[Future[A]]): F[A] =
    self.async { cb =>
      import scala.util.{Failure, Success}

      future.value.onComplete {
        case Failure(e) => cb(Left(e))
        case Success(a) => cb(Right(a))
      }
    }

  def fromFuture(future: Future[A]): F[A] =
    fromFuture(Eval.always(future))
}
