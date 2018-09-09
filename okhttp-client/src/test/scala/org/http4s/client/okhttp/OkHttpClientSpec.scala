package org.http4s
package client
package okhttp

import cats.effect.IO
import cats.implicits._

class OkHttpClientSpec extends ClientRouteTestBattery("OkHttp") {
  def clientResource =
    OkHttpBuilder.withDefaultClient[IO](testBlockingExecutionContext).map(_.create)
}
