package org.http4s
package server
package middleware

import cats.Functor
import cats.data.Kleisli

@deprecated("Use org.http4s.server.middleware.TranslateUri instead", since = "0.18.16")
object URITranslation {
  def translateRoot[F[_], G[_]: Functor, B](prefix: String)(
      @deprecatedName('service) http: Kleisli[F, Request[G], B]): Kleisli[F, Request[G], B] = {
    val newCaret = prefix match {
      case "/" => 0
      case x if x.startsWith("/") => x.length
      case x => x.length + 1
    }

    http.local { req: Request[G] =>
      val oldCaret = req.attributes
        .lookup(Request.Keys.PathInfoCaret)
        .getOrElse(0)
      req.withAttribute(Request.Keys.PathInfoCaret, oldCaret + newCaret)
    }
  }
}
