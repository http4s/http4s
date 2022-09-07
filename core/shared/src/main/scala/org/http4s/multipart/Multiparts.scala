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

package org.http4s.multipart

import cats.Functor
import cats.effect.Sync
import cats.effect.std.Random
import cats.syntax.all._

/** An algebra for creating multipart values and boundaries.
  *
  * A single instance may be shared by the entire application.
  */
trait Multiparts[F[_]] {

  /** Generate a random multipart boundary */
  def boundary: F[Boundary]

  /** Generate a multipart value from Parts with a random multipart boundary */
  def multipart(parts: Vector[Part[F]]): F[Multipart[F]]
}

object Multiparts {

  /** Creates a `cats.effect.std.Random` and provides Multiparts around it.
    * This instance can be shared, or is cheap enough to create closer
    * to where Multipart values are generated.
    */
  def forSync[F[_]](implicit F: Sync[F]): F[Multiparts[F]] =
    Random.scalaUtilRandom[F].map(fromRandom[F])

  def fromRandom[F[_]](random: Random[F])(implicit F: Functor[F]): Multiparts[F] =
    new Multiparts[F] {
      def boundary: F[Boundary] =
        random.nextBytes(30).map(Boundary.unsafeFromBytes)

      def multipart(parts: Vector[Part[F]]): F[Multipart[F]] =
        F.map(boundary)(Multipart(parts, _))
    }
}
