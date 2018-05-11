package org.http4s
package client
package okhttp

import cats.effect.{Effect, IO}

class OkHttpClientSpec
    extends ClientRouteTestBattery(
      "OkHttp",
      OkHttp()(Effect[IO], Http4sSpec.TestExecutionContext).unsafeRunSync()
    )
