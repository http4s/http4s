package org.http4s
package server
package middleware

import scalaz.concurrent.Task

object URITranslation {
  def translateRoot(prefix: String)(service: HttpService): HttpService = {
    val newPrefix = if (!prefix.startsWith("/")) "/" + prefix else prefix

    def translate(path: String): String = newPrefix + (if (path.startsWith("/")) path else "/" + path)

    def transReq(req: Request): Request = {
      val troot = req.attributes.get(translateRootKey)
        .map(_ compose translate)
        .getOrElse(translate(_))

      req.withAttribute(translateRootKey, troot)
        .withPathInfo(req.pathInfo.substring(newPrefix.length))
    }

    service.contramap(transReq)
  }

  val translateRootKey = AttributeKey.http4s[String => String]("translateRoot")
}
