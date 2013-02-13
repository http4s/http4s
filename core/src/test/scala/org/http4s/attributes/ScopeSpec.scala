package org.http4s
package attributes

import org.specs2.mutable.Specification
import concurrent.Future
import play.api.libs.iteratee.{Input, Error, Enumerator}


class ScopeSpec extends Specification {

  val h = new RouteHandler {
    def route: _root_.org.http4s.Route = {
      case x => Error("not used", Input.Empty)
    }
  }
  val req = RequestHead()

  val sess = ThisSession("blah")

  "A list of scopes" should {
    "sort from high to low ranking" in {
      val nw = List(ThisApp(h), ThisContext, sess, ThisServer, ThisRequest(req)).sorted
      nw must_== List(ThisRequest(req), sess, ThisApp(h), ThisContext, ThisServer)
    }
  }
}