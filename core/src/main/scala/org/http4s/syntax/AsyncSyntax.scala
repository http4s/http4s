/*
 * Copyright 2013 http4s.org
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
    self.async_ { cb =>
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
