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
    val newPrefix = if (!prefix.startsWith("/")) "/" + prefix else prefix
    new Route {
      private def stripPath(req: RequestPrelude): Option[RequestPrelude] = {
        if (req.pathInfo.startsWith(newPrefix)) Some(req.copy(pathInfo = req.pathInfo.substring(newPrefix.length)))
        else None
      }

      def apply(req: RequestPrelude): Iteratee[HttpChunk, Responder] =
        in(stripPath(req).getOrElse(throw new MatchError(s"Missing Context: '$newPrefix'")))

      def isDefinedAt(x: RequestPrelude): Boolean = stripPath(x) match {
        case Some(req) => in.isDefinedAt(req)
        case None => false
      }
    }
  }

  def TranslatePath(trans: String => String)(in: Route): Route = new Route {
    def apply(req: RequestPrelude): Iteratee[HttpChunk, Responder] =
        in(req.copy(pathInfo = trans(req.pathInfo)))

    def isDefinedAt(req: RequestPrelude): Boolean =
      in.isDefinedAt(req.copy(pathInfo = trans(req.pathInfo)))
  }

}
