package org.http4s
package client

import cats.effect.IO

class JavaNetClientSpec extends ClientRouteTestBattery("JavaNetClient") {
  def clientResource = JavaNetClientBuilder[IO](testBlockingExecutionContext).resource
}
