package org.http4s

import cats.effect.{Async, Effect, IO, Timer}
import cats.implicits._
import scala.concurrent.ExecutionContext

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
}
