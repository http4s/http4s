package org.http4s

import cats._
import cats.implicits._

final case class Entity(body: EntityBody, length: Option[Long] = None) {
  def +(that: Entity): Entity =
    Entity(this.body ++ that.body, (this.length |@| that.length).map(_ + _))
}

object Entity {
  implicit val entityInstance: Monoid[Entity] =
    new Monoid[Entity] {
      def combine(a1: Entity, a2: Entity): Entity =
        a1 + a2
      val empty: Entity =
        Entity.empty
    }

  lazy val empty = Entity(EmptyBody, Some(0L))
}
