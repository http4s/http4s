/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.Monoid
import cats.syntax.all._

final case class Entity[+F[_]](body: EntityBody[F], length: Option[Long] = None)

object Entity {
  implicit def http4sMonoidForEntity[F[_]]: Monoid[Entity[F]] =
    new Monoid[Entity[F]] {
      def combine(a1: Entity[F], a2: Entity[F]): Entity[F] =
        Entity(a1.body ++ a2.body, (a1.length, a2.length).mapN(_ + _))
      val empty: Entity[F] =
        Entity.empty
    }

  val empty: Entity[Nothing] = Entity[Nothing](EmptyBody, Some(0L))
}
