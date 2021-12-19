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
import cats.~>

sealed trait Entity[+F[_]] {
  def body: EntityBody[F]
  def length: Option[Long]
  def ++[F1[x] >: F[x]](that: Entity[F1]): Entity[F1]
  def translate[F1[x] >: F[x], G[_]](fk: F1 ~> G): Entity[G]
}

object Entity {
  def apply[F[_]](body: EntityBody[F], length: Option[Long] = None): Entity[F] =
    Default(body, length)

  final case class Default[+F[_]](body: EntityBody[F], length: Option[Long]) extends Entity[F] {
    def ++[F1[x] >: F[x]](that: Entity[F1]): Entity[F1] = that match {
      case d: Default[F1] => Default(body ++ d.body, (length, d.length).mapN(_ + _))
    }

    def translate[F1[x] >: F[x], G[_]](fk: F1 ~> G): Entity[G] = Default(body.translate(fk), length)
  }

  implicit def http4sMonoidForEntity[F[_]]: Monoid[Entity[F]] =
    new Monoid[Entity[F]] {
      def combine(a1: Entity[F], a2: Entity[F]): Entity[F] = a1 ++ a2
      val empty: Entity[F] = Entity.empty
    }

  val empty: Entity[Nothing] = Entity[Nothing](EmptyBody, Some(0L))
}
