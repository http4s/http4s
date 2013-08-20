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

sealed trait Scope extends Ordered[Scope] { self =>
  def rank: Int

  def compare(that: Scope) = -(rank compare that.rank)

  private val viewCount = new java.util.concurrent.atomic.AtomicInteger(0)

  private[attributes] def removeView() =  {
    val refCount = viewCount.decrementAndGet()
    if(refCount == 0)  GlobalState.clear(self)
    else if (refCount < 0)  sys.error(s"Invalid reference count: $refCount")
  }

  private[http4s] def newAttributesView() = {
    viewCount.incrementAndGet()
    new AttributesView(GlobalState.forScope(self))(self)
  }
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