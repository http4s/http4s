package org.http4s
package server
package middleware

import cats.Functor
import cats.data.Kleisli

object URITranslation {
  def translateRoot[F[_], G[_]: Functor, A](prefix: String)(
      @deprecatedName('service) http: Kleisli[F, Request[G], A]): Kleisli[F, Request[G], A] = {
    val newCaret = prefix match {
      case "/" => 0
      case x if x.startsWith("/") => x.length
      case x => x.length + 1
    }

    http.local { req: Request[G] =>
      val oldCaret = req.attributes
        .get(Request.Keys.PathInfoCaret)
        .getOrElse(0)
      req.withAttribute(Request.Keys.PathInfoCaret(oldCaret + newCaret))
    }
  }
}
