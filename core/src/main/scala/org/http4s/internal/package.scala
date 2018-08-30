package org.http4s

import cats.effect.{Async, ConcurrentEffect, Effect, IO}
import cats.implicits._
import scala.concurrent.ExecutionContext
import org.log4s.Logger

package object internal {
  // Like fs2.async.unsafeRunAsync before 1.0.  Convenient for when we
  // have an ExecutionContext but not a Timer.
  private[http4s] def unsafeRunAsync[F[_], A](fa: F[A])(
      f: Either[Throwable, A] => IO[Unit])(implicit F: Effect[F], ec: ExecutionContext): Unit =
    F.runAsync(Async.shift(ec) *> fa)(f).unsafeRunSync

  private[http4s] def loggingAsyncCallback[A](logger: Logger)(
      attempt: Either[Throwable, A]): IO[Unit] =
    attempt match {
      case Left(e) => IO(logger.error(e)("Error in asynchronous callback"))
      case Right(_) => IO.unit
    }

  // Inspired by https://github.com/functional-streams-for-scala/fs2/blob/14d20f6f259d04df410dc3b1046bc843a19d73e5/io/src/main/scala/fs2/io/io.scala#L140-L141
  private[http4s] def invokeCallback[F[_]](logger: Logger)(f: => Unit)(implicit F: ConcurrentEffect[F]): Unit =
    F.runAsync(F.start(F.delay(f)).flatMap(_.join))(loggingAsyncCallback(logger)).unsafeRunSync()
}
