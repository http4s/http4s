package org.http4s
package client
package finagle

import cats.effect.IO

class FinagleSpec extends ClientRouteTestBattery("FinagleClient") {
  override def clientResource = Finagle.mkClient[IO](s"${address.getHostName}:${address.getPort}")
}
