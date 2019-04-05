package org.http4s.client.jdkhttpclient

import cats.effect._
import org.http4s.client.ClientRouteTestBattery

class JdkHttpClientSpec extends ClientRouteTestBattery("JdkHttpClient") {
  def clientResource = Resource.liftF(JdkHttpClient[IO]())
}
