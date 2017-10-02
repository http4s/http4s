package org.http4s
package server
package middleware

import cats._
import cats.data.{Kleisli, OptionT}

/** Removes a trailing slash from [[Request]] path
  *
  * If a route exists with a file style [[Uri]], eg "/foo",
  * this middleware will cause [[Request]]s with uri = "/foo" and
  * uri = "/foo/" to match the route.
  */
object AutoSlash {
  def apply[F[_]: Monad](service: HttpService[F]): HttpService[F] =
    Kleisli { req =>
      service(req).orElse {
        val pi = req.pathInfo
        if (pi.isEmpty || pi.charAt(pi.length - 1) != '/')
          OptionT.none
        else
          service.apply(req.withPathInfo(pi.substring(0, pi.length - 1)))
      }
    }
}
