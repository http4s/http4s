package org.http4s
package server
package middleware

import cats._
import cats.data.Kleisli
import cats.implicits._

/** Removes a trailing slash from [[Request]] path
  *
  * If a route exists with a file style [[Uri]], eg "/foo",
  * this middleware will cause [[Request]]s with uri = "/foo" and
  * uri = "/foo/" to match the route.
  */
object AutoSlash {
  def apply[F[_], G[_]: Functor, B](service: Kleisli[F, Request[G], B])(
      implicit M: MonoidK[F]): Kleisli[F, Request[G], B] =
    service <+> Kleisli { req =>
      val pi = req.pathInfo
      if (pi.isEmpty || pi.charAt(pi.length - 1) != '/')
        M.empty
      else
        service.apply(req.withPathInfo(pi.substring(0, pi.length - 1)))
    }
}
