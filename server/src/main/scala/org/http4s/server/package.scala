package org.http4s

import scala.util.control.NoStackTrace
import scalaz.concurrent.Task

package object server {
  /** A Function which defines the transformation of [[Request]] to a scalaz.concurrent.Task[Response]
    * containing the [[Response]]. Unmatched routes will result in the [[Pass]] Exception.
    */
  type HttpService = Request => Task[Response]

  implicit class HttpServiceSyntax(val service: HttpService) extends AnyVal {
    def orElse[T >: Task[Response]](s2: HttpService): HttpService =
      req => service(req).handleWith { case Pass => s2(req) }

    def orNotFound(req: Request): Task[Response] =
      service(req).handleWith { case Pass => ResponseBuilder.notFound(req) }
  }

  case object Pass extends Exception("Unmatched route.") with NoStackTrace
}
