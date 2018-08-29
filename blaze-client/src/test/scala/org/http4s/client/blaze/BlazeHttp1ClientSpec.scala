package org.http4s
package client
package blaze

import cats.effect.IO
import org.http4s.util.threads.newDaemonPoolExecutionContext

class BlazeHttp1ClientSpec extends ClientRouteTestBattery("BlazeClient") {
  def clientResource =
    BlazeClientBuilder[IO](
      newDaemonPoolExecutionContext("blaze-pooled-http1-client-spec", timeout = true)).resource
}
