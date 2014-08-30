package org.http4s.client

import com.typesafe.scalalogging.slf4j.LazyLogging
import org.http4s.server.HttpService
import org.http4s.{ResponseBuilder, Response, Request, Status}

import scalaz.concurrent.Task


class MockClient(route: HttpService) extends Client with LazyLogging {
  /** Prepare a single request
    * @param req [[Request]] containing the headers, URI, etc.
    * @return Task which will generate the Response
    */
  override def prepare(req: Request): Task[Response] = route(req).getOrElse(ResponseBuilder.notFound(req))

  /** Shutdown this client, closing any open connections and freeing resources */
  override def shutdown(): Task[Unit] = Task.now(())
}
