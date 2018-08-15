package org.http4s

import cats.effect.{Async, Effect, IO, Timer}
import cats.implicits._
import scala.concurrent.ExecutionContext
import org.log4s.Logger

package object internal {
  // Like fs2.async.unsafeRunAsync before 1.0.  Convenient for when we
  // have an ExecutionContext but not a Timer.
  private[http4s] def unsafeRunAsync[F[_], A](fa: F[A])(
      f: Either[Throwable, A] => IO[Unit])(implicit F: Effect[F], ec: ExecutionContext): Unit =
    F.runAsync(Async.shift(ec) *> fa)(f).unsafeRunSync

  private[http4s] def blocking[F[_], A](fa: F[A], blockingExecutionContext: ExecutionContext)(
      implicit F: Async[F],
      timer: Timer[F]): F[A] =
    F.bracket(Async.shift[F](blockingExecutionContext))(_ => fa)(_ => timer.shift)

  private[http4s] def loggingAsyncCallback[A](logger: Logger)(
      attempt: Either[Throwable, A]): IO[Unit] =
    attempt match {
      case Left(e) => IO(logger.error(e)("Error in asynchronous callback"))
      case Right(_) => IO.unit
    }
}
