package org.http4s
package server
package middleware

import cats._
import cats.data.Kleisli
import cats.implicits._
import org.http4s.server.middleware.URITranslation.translateRoot

/** Removes a trailing slash from [[Request]] path
  *
  * If a route exists with a file style [[Uri]], eg "/foo",
  * this middleware will cause [[Request]]s with uri = "/foo" and
  * uri = "/foo/" to match the route.
  */
object AutoSlash {
  def apply[F[_], G[_], B](@deprecatedName('service) http: Kleisli[F, Request[G], B])(
      implicit F: MonoidK[F],
      G: Functor[G]): Kleisli[F, Request[G], B] =
    Kleisli { req =>
      http(req) <+> {
        val pi = req.pathInfo
        if (pi.isEmpty || pi.charAt(pi.length - 1) != '/')
          F.empty
        else {
          // Translate the service, in case it comes from a Router with a prefix
          val translated = translateRoot(req.scriptName)(http)
          translated(req.withPathInfo(pi.substring(0, pi.length - 1)))
        }
      }
    }
}
