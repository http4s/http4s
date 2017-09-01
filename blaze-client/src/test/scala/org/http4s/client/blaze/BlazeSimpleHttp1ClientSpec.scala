package org.http4s.client.blaze

import org.http4s.client.ClientRouteTestBattery
import org.http4s.util.threads.newDaemonPoolExecutionContext

class BlazeSimpleHttp1ClientSpec
    extends ClientRouteTestBattery(
      "SimpleHttp1Client",
      SimpleHttp1Client(
        BlazeClientConfig.defaultConfig.copy(executionContext =
          newDaemonPoolExecutionContext("blaze-simple-http1-client-spec", timeout = true)))
    )
