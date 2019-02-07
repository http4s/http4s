package org.http4s
package client
package okhttp

import cats.effect.IO

class OkHttpClientSpec extends ClientRouteTestBattery("OkHttp") {
  def clientResource =
    OkHttpBuilder.withDefaultClient[IO](testBlockingExecutionContext).map(_.create)
}
