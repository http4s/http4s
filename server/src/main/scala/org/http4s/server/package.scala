package org.http4s

import scalaz.concurrent.Task

package object server {
  /** A PartialFunction which defines the transformation of [[Request]] to a scalaz.concurrent.Task[Response]
    * containing the [[Response]]
    */
  type HttpService = PartialFunction[Request, Task[Response]]

  implicit class HttpServiceSyntax(val service: HttpService) extends AnyVal {
    def map(f: Response => Response): HttpService = service.andThen(_.map(f))

    def or(req: Request, resp: => Task[Response]): Task[Response] =
      service.applyOrElse(req, { _: Request => resp })

    def orNotFound(req: Request): Task[Response] = or(req, ResponseBuilder.notFound(req))
  }
}
