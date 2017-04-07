package org.http4s
package server
package blaze

import org.http4s
import scalaz.concurrent.Task
import scalaz.stream.Process

class BlazeServerSpec extends ServerAddressSpec {
  def serverOnPort0 = BlazeServerConfig.default
    .bindHttp(0)
    .start

  "BlazeServer" should {
    // This test just needs to finish to pass, failure will hang
    "Startup and shutdown without blocking" in {
      Process.bracket(serverOnPort0)(s => Process.eval_(Task.delay(s.shutdown))) { _ =>
        Process.emit(true)
      }.runLastOr(false).unsafePerformSync
    }
  }
}
