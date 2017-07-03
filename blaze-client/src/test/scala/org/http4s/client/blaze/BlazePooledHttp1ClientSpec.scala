package org.http4s
package client
package blaze

import org.http4s.util.threads.newDaemonPoolExecutionContext

class BlazePooledHttp1ClientSpec extends ClientRouteTestBattery("Blaze PooledHttp1Client",
  PooledHttp1Client(config = BlazeClientConfig.defaultConfig.copy(customExecutionContext =
    Some(newDaemonPoolExecutionContext("blaze-pooled-http1-client-spec", timeout = true)))))
