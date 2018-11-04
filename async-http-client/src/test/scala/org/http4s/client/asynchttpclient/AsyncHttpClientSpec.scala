package org.http4s
package client
package asynchttpclient

import cats.effect.IO

class AsyncHttpClientSpec extends ClientRouteTestBattery("AsyncHttpClient") {
  def clientResource = AsyncHttpClient.resource[IO]()
}
