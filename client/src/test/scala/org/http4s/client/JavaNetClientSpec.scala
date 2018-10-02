package org.http4s
package client

class JavaNetClientSpec extends ClientRouteTestBattery("JavaNetClient") {
  def clientResource = JavaNetClientBuilder(testBlockingExecutionContext).resource

  // This client streams the request body, but does not allow reads
  // before the write is entirely complete.
  override def maxBufferedBytes = None
}
