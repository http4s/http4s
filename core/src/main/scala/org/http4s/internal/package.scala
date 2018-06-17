package org.http4s

import cats.effect.Async
import cats.implicits._
import scala.concurrent.ExecutionContext

package object internal {
  def blocking[F[_], A](fa: F[A], blockingExecutionContext: ExecutionContext)(implicit F: Async[F], ec: ExecutionContext): F[A] =
    for {
      _ <- Async.shift[F](blockingExecutionContext)
      att <- fa.attempt
      _ <- Async.shift(ec)
      fa0 <- F.fromEither(att)
    } yield fa0
}
