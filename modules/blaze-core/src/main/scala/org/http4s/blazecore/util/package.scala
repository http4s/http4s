/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package blazecore

import cats.effect.Async
import fs2._
import org.http4s.blaze.util.Execution.directec
import scala.concurrent.Future
import scala.util.{Failure, Success}

package object util {

  /** Used as a terminator for streams built from repeatEval */
  private[http4s] val End = Right(None)

  private[http4s] def unNoneTerminateChunks[F[_], I]: Pipe[F, Option[Chunk[I]], I] =
    _.unNoneTerminate.repeatPull {
      _.uncons1.flatMap {
        case Some((hd, tl)) => Pull.output(hd).as(Some(tl))
        case None => Pull.done.as(None)
      }
    }

  private[http4s] val FutureUnit =
    Future.successful(())

  // Adapted from https://github.com/typelevel/cats-effect/issues/199#issuecomment-401273282
  /** Inferior to `Async[F].fromFuture` for general use because it doesn't shift, but
    * in blaze internals, we don't want to shift.
    */
  private[http4s] def fromFutureNoShift[F[_], A](f: F[Future[A]])(implicit F: Async[F]): F[A] =
    F.flatMap(f) { future =>
      future.value match {
        case Some(value) =>
          F.fromTry(value)
        case None =>
          F.async { cb =>
            future.onComplete {
              case Success(a) => cb(Right(a))
              case Failure(t) => cb(Left(t))
            }(directec)
          }
      }
    }

}
