package org.http4s
package util
package middleware

/**
 * @author Bryce Anderson
 *         Created on 3/9/13 at 10:43 AM
 */

object URITranslation {
  def translateRoot[F[_]](prefix: String)(service: HttpService): HttpService = {
    val newPrefix = if (!prefix.startsWith("/")) "/" + prefix else prefix

    {
      case req: Request if req.prelude.pathInfo.startsWith(newPrefix) =>
        service(req.copy(prelude = req.prelude.copy(pathInfo = req.prelude.pathInfo.substring(newPrefix.length))))

      case _ =>
        throw new MatchError(s"Missing Context: '$newPrefix'")
    }
  }

  def translatePath(trans: String => String)(service: HttpService): HttpService = { req: Request =>
    service(req.copy(prelude = req.prelude.copy(pathInfo = trans(req.prelude.pathInfo))))
  }
}
