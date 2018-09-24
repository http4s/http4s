package org.http4s
package client
package jetty

import cats.effect.{IO, Resource}

class JettyClientSpec extends ClientRouteTestBattery("JettyClient") {
  override def clientResource = Resource.make(JettyClient[IO]())(_.shutdown)
}
