package org.http4s
package cooldsl

import org.specs2.mutable._

/**
 * Created by Bryce Anderson on 4/26/14.
 */
class ApiTest extends Specification {

  import CoolApi._

  "CoolDsl Api" should {
    "Combine validators" in {
      val a = CoolApi.require(Header.`Content-Length`)
      val b = CoolApi.requireThat(Header.`Content-Length`){ h => h.length != 0 }

      a and b should_== And(a, b)

      true should_== true
    }

    "Combine status line" in {

      true should_== true
    }
  }

}
