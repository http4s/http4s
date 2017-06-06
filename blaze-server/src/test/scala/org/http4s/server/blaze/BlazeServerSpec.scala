package org.http4s
package server
package blaze

import cats.effect._

class BlazeServerSpec extends ServerAddressSpec {
  def builder = BlazeBuilder[IO]

  "BlazeServer" should {

    // This test just needs to finish to pass, failure will hang
    "Startup and shutdown without blocking" in {
      val s: Server[IO] = BlazeBuilder[IO]
        .bindAny()
        .start
        .unsafeRunSync

      s.shutdownNow()

      true must_== true
    }
  }

}
