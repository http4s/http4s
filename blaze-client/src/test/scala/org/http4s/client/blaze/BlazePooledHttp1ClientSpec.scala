package org.http4s
package client
package blaze

import org.http4s.util.threads.newDaemonPool

class BlazePooledHttp1ClientSpec extends ClientRouteTestBattery("Blaze PooledHttp1Client",
  PooledHttp1Client(config = BlazeClientConfig.defaultConfig.copy(customExecutor =
    Some(newDaemonPool("blaze-pooled-http1-client-spec", timeout = true)))))
