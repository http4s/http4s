package org.http4s
package server
package middleware

import cats.Functor

object URITranslation {
  def translateRoot[F[_]: Functor](prefix: String)(
      @deprecatedName('service, "0.19") routes: HttpRoutes[F]): HttpRoutes[F] = {
    val newCaret = prefix match {
      case "/" => 0
      case x if x.startsWith("/") => x.length
      case x => x.length + 1
    }

    routes.local { req: Request[F] =>
      val oldCaret = req.attributes
        .get(Request.Keys.PathInfoCaret)
        .getOrElse(0)
      req.withAttribute(Request.Keys.PathInfoCaret(oldCaret + newCaret))
    }
  }
}
