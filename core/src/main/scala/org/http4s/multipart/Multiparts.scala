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

import cats.effect.Sync
import cats.syntax.all._

import scala.util.Random

/** An algebra for creating multipart values and boundaries */
trait Multiparts[F[_]] {

  /** Generate a random multipart boundary */
  def boundary: F[Boundary]

  /** Generate a multipart value from Parts with a random multipart boundary */
  def multipart(parts: Vector[Part[F]]): F[Multipart[F]]
}

object Multiparts {
  def fromScalaRandom[F[_]](random: Random)(implicit F: Sync[F]): Multiparts[F] =
    new Multiparts[F] {
      def boundary: F[Boundary] = F
        .delay {
          val bytes = new Array[Byte](30)
          random.nextBytes(bytes)
          bytes
        }
        .map(Boundary.unsafeFromBytes)

      def multipart(parts: Vector[Part[F]]): F[Multipart[F]] =
        F.map(boundary)(Multipart(parts, _))
    }
}
