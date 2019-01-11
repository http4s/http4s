package org.http4s.syntax

import cats.Eval
import cats.effect.Async
import scala.concurrent.{ExecutionContext, Future}

trait AsyncSyntax {
  @deprecated("Has nothing to do with HTTP", "0.19.1")
  implicit def asyncSyntax[F[_], A](async: Async[F]): AsyncOps[F, A] =
    new AsyncOps[F, A](async)
}

final class AsyncOps[F[_], A](val self: Async[F]) extends AnyVal {
  @deprecated("Has nothing to do with HTTP. Use `IO.fromFuture(IO(future)).to[F]`", "0.19.1")
  def fromFuture(future: Eval[Future[A]])(implicit ec: ExecutionContext): F[A] =
    self.async { cb =>
      import scala.util.{Failure, Success}

      future.value.onComplete {
        case Failure(e) => cb(Left(e))
        case Success(a) => cb(Right(a))
      }
    }

  @deprecated("Has nothing to do with HTTP. Use `IO.fromFuture(IO(future)).to[F]`", "0.19.1")
  def fromFuture(future: => Future[A])(implicit ec: ExecutionContext): F[A] =
    fromFuture(Eval.always(future))
}
