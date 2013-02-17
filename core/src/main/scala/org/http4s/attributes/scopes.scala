package org.http4s
package attributes

import scala.language.implicitConversions
import scalaz._
import scala.Ordering

object Scope {
  implicit def req2scope(req: RequestPrelude) = ThisRequest(req)
  implicit def routeHandler2Scope(handler: RouteHandler) = attributes.ThisApp(handler)

  implicit object ScopeOrdering extends Ordering[Scope] {
    def compare(x: Scope, y: Scope): Int = -(x.rank compare y.rank)
  }

//  sealed trait ThisServer
//
//  def ThisServer: attributes.ThisServer.type @@ ThisServer = Tag[ThisServer.type, ThisServer](attributes.ThisServer)
//
//  sealed trait ThisApp
//  def ThisApp(a: attributes.ThisApp): attributes.ThisApp @@ ThisApp = Tag[attributes.ThisApp, ThisApp](a)
//
//  sealed trait ThisRequest
//  def ThisRequest()
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
case class ThisRequest(request: RequestPrelude) extends RequestScope {
  val rank = 1000
}