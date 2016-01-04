package org.http4s.client

import org.http4s.server._
import org.http4s.{Response, Request}

import scalaz.concurrent.Task

class MockClient(service: HttpService) extends Client {
  /** Prepare a single request
    * @param req [[Request]] containing the headers, URI, etc.
    * @return Task which will generate the Response
    */
  override def open(req: Request): Task[DisposableResponse] =
    service(req).map { resp => DisposableResponse(resp, Task.now(())) }

  /** Shutdown this client, closing any open connections and freeing resources */
  override def shutdown(): Task[Unit] = Task.now(())
}
