package org.http4s
package util
package middleware

import org.http4s.{Response, HttpChunk, HttpService, RequestPrelude}

/**
 * @author Bryce Anderson
 *         Created on 3/9/13 at 10:43 AM
 */

object URITranslation {
  def translateRoot[F[_]](prefix: String)(service: HttpService[F]): HttpService[F] = {
    val newPrefix = if (!prefix.startsWith("/")) "/" + prefix else prefix

    {
      case req: Request[F] if req.prelude.pathInfo.startsWith(newPrefix) =>
        service(req.copy(prelude = req.prelude.copy(pathInfo = req.prelude.pathInfo.substring(newPrefix.length))))

      case _ =>
        throw new MatchError(s"Missing Context: '$newPrefix'")
    }
  }

  def translatePath[F[_]](trans: String => String)(service: HttpService[F]): HttpService[F] = { req: Request[F] =>
    service(req.copy(prelude = req.prelude.copy(pathInfo = trans(req.prelude.pathInfo))))
  }
}
