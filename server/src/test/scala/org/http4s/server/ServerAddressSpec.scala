package org.http4s
package server

import java.net.InetSocketAddress
import fs2._
import org.specs2.mutable.After

trait ServerAddressSpec extends Http4sSpec {
  def builder: ServerBuilder

  trait ServerContext extends After {
    import Http4sSpec.TestPoolStrategy
    val address = new InetSocketAddress(0)
    val server = builder.bindSocketAddress(address).start.async.unsafeRun
    def after = server.shutdown.unsafeRun
  }

  "A server configured with port 0" should {
    "know its local port after start" in new ServerContext {
      server.address.getPort must be_> (0)
    }
  }
}
