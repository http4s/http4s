package org.http4s
package client
package asynchttpclient

import cats.effect.{ConcurrentEffect, IO}
import scala.concurrent.ExecutionContext.Implicits.global

class AsyncHttpClientSpec
    extends ClientRouteTestBattery(
      "AsyncHttpClient",
      AsyncHttpClient()(ConcurrentEffect[IO], Http4sSpec.TestExecutionContext))
