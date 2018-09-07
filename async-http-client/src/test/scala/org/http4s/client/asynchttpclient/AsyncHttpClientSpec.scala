package org.http4s
package client
package asynchttpclient

import cats.effect.{IO, Resource}

class AsyncHttpClientSpec extends ClientRouteTestBattery("AsyncHttpClient") {
  def clientResource = Resource.make(IO(AsyncHttpClient()))(_.shutdown)
}
