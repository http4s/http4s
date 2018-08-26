package org.http4s
package client
package jetty

import cats.effect.{ConcurrentEffect, IO}
import scala.concurrent.ExecutionContext.Implicits.global

class JettyClientSpec
    extends ClientRouteTestBattery(
      "JettyClient",
      JettyClient()(ConcurrentEffect[IO], Http4sSpec.TestExecutionContext).unsafeRunSync())
