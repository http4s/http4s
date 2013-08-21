package org.http4s
package attributes

import org.specs2.mutable.Specification
import concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.{Input, Error, Enumerator}
import java.util.UUID


class ScopeSpec extends Specification {


  "A list of scopes" should {
    "sort from high to low ranking" in {
      val appScope = new AppScope
      val reqScope = new RequestScope
      val valScope = new ValueScope
      val nw = List(GlobalScope, appScope, reqScope, valScope).sorted
      nw must_== List(valScope, reqScope, appScope, GlobalScope)
    }
  }
}