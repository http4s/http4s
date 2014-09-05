package org.http4s
package server
package middleware

import scalaz.concurrent.Task

object URITranslation {
  def translateRoot(prefix: String)(service: HttpService): HttpService = new HttpService {

    private val newPrefix = if (!prefix.startsWith("/")) "/" + prefix else prefix

    private def translate(path: String): String = newPrefix + (if (path.startsWith("/")) path else "/" + path)

    private def transReq(req: Request): Request = {
      val troot = req.attributes.get(translateRootKey)
                    .map(_ compose translate)
                    .getOrElse(translate(_))

      req.withAttribute(translateRootKey, troot)
         .withPathInfo(req.pathInfo.substring(newPrefix.length))
    }

    def isDefinedAt(x: Request): Boolean = {
      x.pathInfo.startsWith(newPrefix) && service.isDefinedAt(transReq(x))
    }

    def apply(r: Request): Task[Response] = service.apply(transReq(r))

    override def applyOrElse[A1 <: Request, B1 >: Task[Response]](x: A1, default: (A1) => B1): B1 = {
      if (x.pathInfo.startsWith(newPrefix)) {
        val req = transReq(x)
        if (service.isDefinedAt(req)) service.apply(req)
        else default(x)
      } else default(x)
    }
  }

  val translateRootKey = AttributeKey.http4s[String => String]("translateRoot")
}
