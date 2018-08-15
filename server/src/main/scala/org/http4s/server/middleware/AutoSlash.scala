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
  def apply[F[_], G[_], B](@deprecatedName('service) http: Kleisli[F, Request[G], B])(
      implicit F: MonoidK[F],
      G: Functor[G]): Kleisli[F, Request[G], B] =
    Kleisli { req =>
      http(req) <+> {
        val pathInfo = req.pathInfo
        val scriptName = req.scriptName

        if (pathInfo.isEmpty || pathInfo.charAt(pathInfo.length - 1) != '/') {
          F.empty
        } else if (scriptName.isEmpty) {
          // Request has not been translated already
          http.apply(req.withPathInfo(pathInfo.substring(0, pathInfo.length - 1)))
        } else {
          // Request has been translated at least once, redo the translation
          val translated = TranslateUri(scriptName)(http)
          translated.apply(req.withPathInfo(pathInfo.substring(0, pathInfo.length - 1)))
        }
      }
    }
}
