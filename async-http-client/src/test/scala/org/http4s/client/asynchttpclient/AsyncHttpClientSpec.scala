package org.http4s.client.asynchttpclient

import org.http4s.client.ClientRouteTestBattery
import org.http4s.testing.ThreadDumpOnTimeout

import scala.concurrent.duration._

class AsyncHttpClientSpec extends ClientRouteTestBattery("AsyncHttpClient", AsyncHttpClient()) with ThreadDumpOnTimeout {
  override val triggerThreadDumpAfter = timeout - 500.millis
}
