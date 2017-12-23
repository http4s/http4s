package org.http4s.client.blaze

import org.http4s.client.ClientRouteTestBattery
import org.http4s.util.threads.newDaemonPoolExecutionContext

@deprecated("Well, we still need to test it", "0.18.0-M7")
class BlazeSimpleHttp1ClientSpec
    extends ClientRouteTestBattery(
      "SimpleHttp1Client",
      SimpleHttp1Client(
        BlazeClientConfig.defaultConfig.copy(executionContext =
          newDaemonPoolExecutionContext("blaze-simple-http1-client-spec", timeout = true)))
    )
