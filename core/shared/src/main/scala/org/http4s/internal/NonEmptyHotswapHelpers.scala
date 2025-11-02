/*
 * Copyright 2025 http4s.org
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

package org.http4s.internal

import cats.effect.kernel.MonadCancel
import cats.effect.std.NonEmptyHotswap
import cats.syntax.all._

private[http4s] object NonEmptyHotswapHelpers {
  def requireSome[F[_], A](opt: Option[A], message: => String)(implicit
      F: MonadCancel[F, Throwable]
  ): F[A] =
    opt.liftTo[F](new IllegalStateException(message))

  def requireCurrent[F[_], A](hs: NonEmptyHotswap[F, Option[A]], message: => String)(implicit
      F: MonadCancel[F, Throwable]
  ): F[A] =
    hs.get.use(requireSome[F, A](_, message))
}
