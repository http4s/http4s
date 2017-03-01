package org.http4s
package server

import java.net.InetSocketAddress
import org.specs2.mutable.After
import scalaz.{\/-, -\/}
import scalaz.concurrent.Task

trait ServerAddressSpec extends Http4sSpec {
  def builder: ServerBuilder

  trait ServerContext extends After {
    val address = new InetSocketAddress(0)
    val server = Task.fork(builder.bindSocketAddress(address).start).unsafePerformSync
    def after = server.shutdown.unsafePerformSync
  }

  "A server configured with port 0" should {
    "know its local port after start" in new ServerContext {
      server.address.getPort must be_> (0)
    }
  }
}
