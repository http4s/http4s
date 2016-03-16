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
    val server = Task.fork(builder.bindSocketAddress(address).start).run
    def after = server.shutdown.run
  }

  "A server configured with port 0" should {
    "know its local port after start" in new ServerContext {
      server.address.getPort must be_> (0)
    }
  }
}
