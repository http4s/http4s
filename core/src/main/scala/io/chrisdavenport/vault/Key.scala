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

package io.chrisdavenport.vault

import cats.effect.Sync
import cats.Hash
import cats.implicits._
import io.chrisdavenport.unique.Unique

/** A unique value tagged with a specific type to that unique.
  * Since it can only be created as a result of that, it links
  * a Unique identifier to a type known by the compiler.
  */
final class Key[A] private (private[vault] val unique: Unique) {
  override def hashCode(): Int = unique.hashCode()
}

object Key {

  /** Create A Typed Key
    */
  def newKey[F[_]: Sync, A]: F[Key[A]] = Unique.newUnique[F].map(new Key[A](_))

  implicit def keyInstances[A]: Hash[Key[A]] = new Hash[Key[A]] {
    // Members declared in cats.kernel.Eq
    def eqv(x: Key[A], y: Key[A]): Boolean =
      x.unique === y.unique

    // Members declared in cats.kernel.Hash
    def hash(x: Key[A]): Int = Hash[Unique].hash(x.unique)
  }
}
