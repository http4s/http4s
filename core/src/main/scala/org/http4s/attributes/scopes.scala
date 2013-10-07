package org.http4s
package attributes

import scala.language.implicitConversions
import scala.Ordering
import java.util.UUID
import scala.collection.concurrent.TrieMap

object Scope {

  implicit object ScopeOrdering extends Ordering[Scope] {
    def compare(x: Scope, y: Scope): Int = -(x.rank compare y.rank)
  }

}

sealed trait Scope extends Ordered[Scope] with ScopedAttributes { self =>
  def rank: Int

  lazy val underlying = new TrieMap[Key[_], Any]

  def compare(that: Scope) = -(rank compare that.rank)

  def scope = self
}

object GlobalScope extends ServerScope { override def rank: Int = 1 }

trait ServerScope extends Scope { self =>
  def rank = 10
}

class AppScope extends Scope {
  def rank = 100
}

class RequestScope extends Scope {
  def rank = 1000
}

class ValueScope extends Scope {
  def rank = 10000
}
