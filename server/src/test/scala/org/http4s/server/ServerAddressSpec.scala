package org.http4s
package server

import java.net.InetSocketAddress

import cats.effect.IO
import org.specs2.mutable.After

trait ServerAddressSpec extends Http4sSpec {
  def builder: ServerBuilder[IO]

  trait ServerContext extends After {
    val address = new InetSocketAddress(0)
    val server = builder.bindSocketAddress(address).start.unsafeRunSync()
    def after = server.shutdown.unsafeRunSync()
  }

  "A server configured with port 0" should {
    "know its local port after start" in new ServerContext {
      server.address.getPort must be_> (0)
    }
  }
}
