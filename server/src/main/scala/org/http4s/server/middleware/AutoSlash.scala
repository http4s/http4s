package org.http4s
package server
package middleware

import cats._
import cats.implicits._

/** Removes a trailing slash from [[Request]] path
  *
  * If a route exists with a file style [[Uri]], eg "/foo",
  * this middleware will cause [[Request]]s with uri = "/foo" and
  * uri = "/foo/" to match the route.
  */
object AutoSlash {
  def apply[F[_]](service: HttpService[F])(implicit F: Monad[F]): HttpService[F] = Service.lift {
    req =>
      service(req).flatMap {
        case Pass() =>
          val pi = req.pathInfo
          if (pi.isEmpty || pi.charAt(pi.length - 1) != '/')
            Pass.pure[F]
          else {
            service.apply(req.withPathInfo(pi.substring(0, pi.length - 1)))
          }
        case resp =>
          F.pure(resp)
      }
  }
}
