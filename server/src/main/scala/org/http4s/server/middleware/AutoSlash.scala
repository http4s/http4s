package org.http4s
package server
package middleware

import cats.{Functor, Monad, MonoidK}
import cats.data.Kleisli
import cats.implicits._

/** Removes a trailing slash from [[Request]] path
  *
  * If a route exists with a file style [[Uri]], eg "/foo",
  * this middleware will cause [[Request]]s with uri = "/foo" and
  * uri = "/foo/" to match the route.
  */
object AutoSlash {
  def apply[F[_], G[_], B](http: Kleisli[F, Request[G], B])(
      implicit F: MonoidK[F],
      G: Functor[G]): Kleisli[F, Request[G], B] = {
    val _ = G // for binary compatibility in 0.20, remove on master
    Kleisli { req =>
      http(req) <+> {
        val pathInfo = req.pathInfo

        if (pathInfo.isEmpty || pathInfo.charAt(pathInfo.length - 1) != '/') {
          F.empty
        } else {
          http.apply(req.withPathInfo(pathInfo.substring(0, pathInfo.length - 1)))
        }
      }
    }
  }

  def httpRoutes[F[_]: Monad](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(httpRoutes)

  def httpApp[F[_]: MonoidK: Functor](httpApp: HttpApp[F]): HttpApp[F] =
    apply(httpApp)
}
