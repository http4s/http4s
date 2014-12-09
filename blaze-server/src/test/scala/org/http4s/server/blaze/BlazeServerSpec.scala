package org.http4s.server.blaze

import org.specs2.mutable.Specification

class BlazeServerSpec extends Specification {

  "BlazeServer" should {

    // This test just needs to finish to pass, failure will hang
    "Startup and shutdown without blocking" in {
      val s = BlazeBuilder
        .bindAny()
        .start.run

      s.shutdownNow()

      true must_== true
    }
  }

}
