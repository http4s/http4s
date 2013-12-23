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

    def translate(path: String): String = newPrefix + (if (path.startsWith("/")) path else "/" + path)

    {
      case req: Request if req.pathInfo.startsWith(newPrefix) =>

        val troot = req.attributes.get(translateRootKey)
                    .map(_ compose translate)
                    .getOrElse(translate(_))

        service(req
          .withAttribute(translateRootKey, troot)
          .withPathInfo(req.pathInfo.substring(newPrefix.length)) )

      case req =>
        throw new MatchError(s"Missing Context: '$newPrefix' \nRequested: ${req.pathInfo}")
    }
  }

  val translateRootKey = AttributeKey.http4s[String => String]("translateRoot")
}
