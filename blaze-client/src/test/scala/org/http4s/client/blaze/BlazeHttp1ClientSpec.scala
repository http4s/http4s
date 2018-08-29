package org.http4s
package client
package blaze

import cats.effect.IO
import org.http4s.util.threads.newDaemonPoolExecutionContext

class BlazeHttp1ClientSpec extends {
  implicit val testContextShift = Http4sSpec.TestContextShift
} with ClientRouteTestBattery(
  "Blaze Http1Client",
  BlazeClientBuilder[IO](
    newDaemonPoolExecutionContext("blaze-pooled-http1-client-spec", timeout = true)).resource
)
