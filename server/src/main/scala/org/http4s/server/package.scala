package org.http4s

import scalaz.concurrent.Task

package object server {
  /** Defines the transformation of [[Request]] to a scalaz.concurrent.Task[Response]
    * containing the [[Response]].  A service may optionally return a Task failed with
    * [[Pass]] to delegate.  Unhandled Pass failures are treated as a 404.
    */
  type HttpService = Service[Request, Response]

  implicit class HttpServiceSyntax(val service: HttpService) extends AnyVal {
    def orNotFound(req: Request): Task[Response] = service.or(req, ResponseBuilder.notFound(req))
  }
}
