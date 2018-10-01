package org.http4s
package client

class JavaNetClientSpec extends ClientRouteTestBattery("JavaNetClient") {
  def clientResource = JavaNetClientBuilder(testBlockingExecutionContext).resource

  // This client is incapable of backpressure
  override def maxBufferedBytes = None
}
