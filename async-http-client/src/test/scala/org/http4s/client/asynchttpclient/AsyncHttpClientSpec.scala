package org.http4s.client.asynchttpclient

import org.http4s.client.ClientRouteTestBattery
import org.http4s.util.threads.newDaemonPool

class AsyncHttpClientSpec extends ClientRouteTestBattery("AsyncHttpClient",
  AsyncHttpClient(bufferSize = 1,
    customExecutor = Some(newDaemonPool("async-http-client-spec", timeout = true))))
