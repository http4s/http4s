package org.http4s
package client
package jetty

import cats.effect.IO

class JettyClientSpec extends ClientRouteTestBattery("JettyClient") {
  override def clientResource = JettyClient.resource[IO]()
}
