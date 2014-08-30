package org.http4s

import scalaz.concurrent.Task

package object server {
  /** A PartialFunction which defines the transformation of [[Request]] to a scalaz.concurrent.Task[Response]
    * containing the [[Response]]
    */
  type HttpService = Request => Option[Task[Response]]

  implicit class HttpServiceSyntax(val service: HttpService) extends AnyVal {
    def orElse[T >: Task[Response]](f: Request => Option[T]): Request => Option[T] =
      req => service(req) orElse f(req)
  }
}
