/*
 * Copyright 2019 http4s.org
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

package io.chrisdavenport.keypool

import cats.Functor
import cats.effect.Ref

/** A managed Resource.
  *
  * This knows whether it was reused or not, and
  * has a reference that when it leaves the controlling
  * scope will dictate whether it is shutdown or returned
  * to the pool.
  */
final class Managed[F[_], A] private[keypool] (
    val value: A,
    val isReused: Boolean,
    val canBeReused: Ref[F, Reusable]
)

object Managed {
  implicit def managedFunctor[F[_]]: Functor[Managed[F, *]] = new Functor[Managed[F, *]] {
    def map[A, B](fa: Managed[F, A])(f: A => B): Managed[F, B] = new Managed[F, B](
      f(fa.value),
      fa.isReused,
      fa.canBeReused
    )
  }
}
