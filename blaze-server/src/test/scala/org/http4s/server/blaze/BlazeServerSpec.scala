package org.http4s
package server
package blaze

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

class BlazeServerSpec extends ServerAddressSpec {
  def builder = BlazeBuilder

  "BlazeServer" should {

    // This test just needs to finish to pass, failure will hang
    "Startup and shutdown without blocking" in {
      val s = BlazeBuilder
        .bindAny()
        .run

      s.shutdownNow()

      true must_== true
    }

  }

}
