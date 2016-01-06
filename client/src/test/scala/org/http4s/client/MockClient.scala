package org.http4s
package client

import scalaz.concurrent.Task

object MockClient {
  def apply(service: HttpService, dispose: Task[Unit] = Task.now(())) = Client(
    open = service.map(resp => DisposableResponse(resp, dispose)),
    shutdown = Task.now(())
  )
}
