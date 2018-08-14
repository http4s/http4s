package org.http4s
package server
package middleware

import cats.Functor

@deprecated("Use org.http4s.server.middleware.TranslateUri instead", since = "0.18.16")
object URITranslation {
  def translateRoot[F[_]: Functor](prefix: String)(service: HttpService[F]): HttpService[F] = {
    val newCaret = prefix match {
      case "/" => 0
      case x if x.startsWith("/") => x.length
      case x => x.length + 1
    }

    service.local { req: Request[F] =>
      val oldCaret = req.attributes
        .get(Request.Keys.PathInfoCaret)
        .getOrElse(0)
      req.withAttribute(Request.Keys.PathInfoCaret(oldCaret + newCaret))
    }
  }
}
