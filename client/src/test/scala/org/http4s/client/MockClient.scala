package org.http4s
package client

import scalaz.concurrent.Task

object MockClient {
  def apply(service: HttpService) = Client(
    open = service.map(resp => DisposableResponse(resp, Task.now(()))),
    shutdown = Task.now(())
  )
}
