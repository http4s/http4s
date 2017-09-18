package org.http4s
package client
package asynchttpclient

import cats.effect.{Effect, IO}

class AsyncHttpClientSpec
    extends ClientRouteTestBattery(
      "AsyncHttpClient",
      AsyncHttpClient()(Effect[IO], Http4sSpec.TestExecutionContext))
