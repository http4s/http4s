package org.http4s
package client
package okhttp

import cats.effect.IO
import cats.implicits._
import org.http4s.Http4sSpec._

class OkHttpClientSpec
  extends ClientRouteTestBattery(
    "OkHttp",
    OkHttp.default[IO](TestBlockingExecutionContext).map(_.create(implicitly, TestContextShift)))
