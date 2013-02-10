package org.http4s
package attributes

object Scope {
  implicit def req2scope[T](req: Request[T]) = ThisRequest(req)

  implicit object ScopeOrdering extends Ordering[Scope] {
    def compare(x: Scope, y: Scope): Int = x.rank compare y.rank
  }
}

sealed trait Scope {
  def rank: Int
}

sealed trait AppScope extends Scope
object ThisServer extends AppScope {
  val rank = 0
}
object ThisContext extends AppScope {
  val rank = 5
}
case class ThisApp(route: Route) extends AppScope {
  val rank = 50
}

sealed trait RequestScope extends Scope
case class ThisRequest[T](request: Request[T]) {
  val rank = 100
}