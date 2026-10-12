/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

import cats.effect.kernel.MonadCancelThrow
import cats.effect.Resource
import cats.syntax.all._

/** Observe the consumed result before an acquired response can manufacture a wire verdict by closing. */
private[wire] object ClientObservation {
  def apply[F[_]: MonadCancelThrow, A, B](resource: Resource[F, A])(
      consume: A => F[B]
  )(observe: Either[Throwable, B] => F[Unit]): F[Unit] =
    resource.attempt.use {
      case Left(error) => observe(Left(error))
      case Right(value) => consume(value).attempt.flatMap(observe)
    }
}
