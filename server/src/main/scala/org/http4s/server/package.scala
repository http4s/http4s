package org.http4s

import scalaz.OptionT
import scalaz.concurrent.Task
import scalaz.syntax.bind._

package object server {
  /** A PartialFunction which defines the transformation of [[Request]] to a scalaz.concurrent.Task[Response]
    * containing the [[Response]]
    */
  type HttpService = Request => OptionT[Task, Response]

  implicit class HttpServiceSyntax(val service: HttpService) extends AnyVal {
    def or(req: Request, resp: Task[Response]): Task[Response] =
      service(req).fold(Task.now, resp).join

    def orElse(fallback: HttpService): HttpService = { req: Request =>
      service(req) orElse fallback(req)
    }
  }
}
