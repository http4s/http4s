package org.http4s
package attributes

import scala.language.implicitConversions

object Scope {
  implicit def req2scope(req: RequestHead) = ThisRequest(req)
  implicit def routeHandler2Scope(handler: RouteHandler) = ThisApp(handler)

  implicit object ScopeOrdering extends Ordering[Scope] {
    def compare(x: Scope, y: Scope): Int = -(x.rank compare y.rank)
  }
}

sealed trait Scope extends Ordered[Scope] {
  def rank: Int

  def compare(that: Scope) = -(rank compare that.rank)
}

sealed trait AppScope extends Scope
object ThisServer extends AppScope {
  val rank = 0
}
case class ThisApp(route: RouteHandler) extends AppScope {
  val rank = 100
}

sealed trait RequestScope extends Scope
case class ThisRequest(request: RequestHead) extends RequestScope {
  val rank = 1000
}