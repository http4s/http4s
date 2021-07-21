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

package org.http4s

import cats.Monoid
import cats.syntax.all._

final case class Entity[+Body](body: Body, length: Option[Long] = None)

object Entity {
  implicit def http4sMonoidForEntity[B: Monoid]: Monoid[Entity[B]] =
    new Monoid[Entity[B]] {
      def combine(a1: Entity[B], a2: Entity[B]): Entity[B] =
        Entity(
          body = a1.body.mappend(a2.body),
          length = (a1.length, a2.length).mapN(_ + _)
        )

      val empty: Entity[B] = Monoid[B].empty
    }

  val empty: Entity[Unit] = Entity[Unit]((), Some(0L))
}
