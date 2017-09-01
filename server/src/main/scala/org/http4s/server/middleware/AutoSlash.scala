package org.http4s
package server
package middleware

import cats._
import cats.implicits._
import fs2._

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
        case _: Pass[F] =>
          val p = req.uri.path
          if (p.isEmpty || p.charAt(p.length - 1) != '/')
            F.pure(Pass[F])
          else {
            val withSlash = req.withUri(req.uri.copy(path = p.substring(0, p.length - 1)))
            service.apply(withSlash)
          }
        case resp =>
          F.pure(resp)
      }
  }
}
