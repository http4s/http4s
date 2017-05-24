package org.http4s

import fs2.util.Lub1
import cats._
import cats.implicits._

final case class Entity[+F[_]](body: EntityBody[F], length: Option[Long] = None) {
  def +[G[_], Lub[_]](that: Entity[G])(implicit L: Lub1[F, G, Lub]): Entity[Lub] =
    Entity(this.body ++ that.body, (this.length |@| that.length).map(_ + _))
}

object Entity {
  implicit def entityInstance[F[_]]: Monoid[Entity[F]] =
    new Monoid[Entity[F]] {
      def combine(a1: Entity[F], a2: Entity[F]): Entity[F] =
        a1 + a2
      val empty: Entity[F] =
        Entity.empty
    }

  val empty = Entity[Nothing](EmptyBody, Some(0L))
}
