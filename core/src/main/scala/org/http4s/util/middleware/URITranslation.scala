package org.http4s.util
package middleware

import org.http4s.{Responder, HttpChunk, Route, RequestPrelude}
import play.api.libs.iteratee.Iteratee

/**
 * @author Bryce Anderson
 *         Created on 3/9/13 at 10:43 AM
 */

object URITranslation {

  def TranslateRoot(prefix: String)(in: Route): Route = {
    val newPrefix = prefix + "/"
    val trans: String => String = { str =>
      if(str.startsWith(newPrefix))
        str.substring(prefix.length)
      else str
    }
    TranslatePath(in)(trans)
  }

  def TranslatePath(in: Route)(trans: String => String): Route = new Route {
    def apply(req: RequestPrelude): Iteratee[HttpChunk, Responder] =
        in(req.copy(pathInfo = trans(req.pathInfo)))

    def isDefinedAt(req: RequestPrelude): Boolean =
      in.isDefinedAt(req.copy(pathInfo = trans(req.pathInfo)))
  }

}
