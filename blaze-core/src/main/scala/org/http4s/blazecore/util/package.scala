/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package blazecore

import cats.effect.Async
import org.http4s.blaze.util.Execution.directec

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

package object util {

  /** Used as a terminator for streams built from repeatEval */
  private[http4s] val End = Right(None)

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

  private[http4s] def fromFutureNoShiftUncancelable[F[_], A](
      f: F[Future[A]]
  )(implicit F: Async[F]): F[A] =
    bracketBasedUncancelable(fromFutureNoShift(f))

  private def bracketBasedUncancelable[F[_], A](fa: F[A])(implicit F: Async[F]): F[A] =
    // unlike uncancelable, bracket seems to make cancel wait for itself in CE2
    F.bracket(fa)(a => F.pure(a))(_ => F.unit)

}
