package org.http4s
package server
package middleware

import cats._
import cats.data.{Kleisli, OptionT}
import org.http4s.server.middleware.URITranslation.translateRoot

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
        val pathInfo = req.pathInfo
        val scriptName = req.scriptName

        if (pathInfo.isEmpty || pathInfo.charAt(pathInfo.length - 1) != '/') {
          OptionT.none
        } else if (scriptName.isEmpty) {
          // Request has not been translated already
          service.apply(req.withPathInfo(pathInfo.substring(0, pathInfo.length - 1)))
        } else {
          // Request has been translated at least once, redo the translation
          val translated = translateRoot(scriptName)(service)
          translated.apply(req.withPathInfo(pathInfo.substring(0, pathInfo.length - 1)))
        }
      }
    }
}
