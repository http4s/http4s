package org.http4s
package attributes

import scala.language.implicitConversions
import scala.Ordering
import java.util.UUID

object Scope {

  implicit object ScopeOrdering extends Ordering[Scope] {
    def compare(x: Scope, y: Scope): Int = -(x.rank compare y.rank)
  }

}

sealed trait Scope extends Ordered[Scope] {
  def rank: Int

  def compare(that: Scope) = -(rank compare that.rank)
}

object ThisServer extends Scope {
  val rank = 0
}
case class AppScope(uuid: UUID = UUID.randomUUID()) extends Scope {
  val rank = 100
}

case class RequestScope(uuid: UUID) extends Scope {
  val rank = 1000
}