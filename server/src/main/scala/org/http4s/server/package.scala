package org.http4s

import scalaz.OptionT
import scalaz.concurrent.Task
import scalaz.syntax.bind._

package object server {
  /**
   * An HTTP service transforms a [[Request]] into a [[Response]] optionally
   * and asynchronously.
   *
   * An HTTP service may choose not to respond by returning [[NoResponse]].  [[HttpServiceSyntax]]
   * provides methods to provide a fallback response from another HTTP service or a fixed response
   * when this service returns [[NoResponse]].
   *
   * @see [[HttpService.apply]] to create an HTTP service from a [[PartialFunction]]
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

  /**
   * Signifies that an HTTP service has declined to respond to the request.
   */
  val NoResponse: OptionT[Task, Response] = OptionT.none
}
