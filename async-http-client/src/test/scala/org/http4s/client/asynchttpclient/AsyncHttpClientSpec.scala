package org.http4s
package client
package asynchttpclient

class AsyncHttpClientSpec extends ClientRouteTestBattery("AsyncHttpClient",
  AsyncHttpClient(bufferSize = 1)(Http4sSpec.TestExecutionContext))
