package org.http4s.client

import org.http4s.server._
import org.http4s.{ResponseBuilder, Response, Request, Status}

import scalaz.concurrent.Task

class MockClient(service: HttpService) extends Client {
  /** Prepare a single request
    * @param req [[Request]] containing the headers, URI, etc.
    * @return Task which will generate the Response
    */
  override def prepare(req: Request): Task[Response] = service.or(req, ResponseBuilder.notFound(req))

  /** Shutdown this client, closing any open connections and freeing resources */
  override def shutdown(): Task[Unit] = Task.now(())
}
