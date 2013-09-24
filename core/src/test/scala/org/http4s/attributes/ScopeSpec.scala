package org.http4s
package attributes

import org.specs2.mutable.Specification
import concurrent.{ExecutionContext, Future}
import java.util.UUID


class ScopeSpec extends Specification {


  "A list of scopes" should {
    "sort from high to low ranking" in {
      val appid = UUID.randomUUID()
      val reqid = UUID.randomUUID()
      val nw = List(AppScope(appid), ThisServer, RequestScope(reqid)).sorted
      nw must_== List(RequestScope(reqid), AppScope(appid), ThisServer)
    }
  }
}