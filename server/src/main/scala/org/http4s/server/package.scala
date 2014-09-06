package org.http4s

import scalaz.OptionT
import scalaz.concurrent.Task
import scalaz.syntax.bind._

package object server {
  /**
   * An HttpService transforms a [[Request]] into a [[Response]] optionally and
   * asynchronously.
   */
  type HttpService = Request => OptionT[Task, Response]

  implicit class HttpServiceSyntax(val service: HttpService) extends AnyVal {
    def map(f: Response => Response): HttpService = service.andThen(_.map(f))

    def or(req: Request, resp: => Task[Response]): Task[Response] =
      service(req).fold(Task.now, resp).join

    def orNotFound(req: Request): Task[Response] = or(req, ResponseBuilder.notFound(req))

    def orElse(fallback: HttpService): HttpService = { req: Request =>
      service(req) orElse fallback(req)
    }
  }
}
