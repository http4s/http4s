package org.http4s
package util
package middleware

/**
 * @author Bryce Anderson
 *         Created on 3/9/13 at 10:43 AM
 */

object URITranslation {
  def translateRoot(prefix: String)(service: HttpService): HttpService = {
    val newPrefix = if (!prefix.startsWith("/")) "/" + prefix else prefix

    {
      case req: Request if req.pathInfo.startsWith(newPrefix) =>
        service(req.withPathInfo(req.pathInfo.substring(newPrefix.length)))

      case req =>
        throw new MatchError(s"Missing Context: '$newPrefix' \nRequested: ${req.pathInfo}")
    }
  }

  def translatePath(trans: String => String)(service: HttpService): HttpService = { req: Request =>
    service(req.withPathInfo(trans(req.pathInfo)))
  }
}
