package org.http4s
package server
package middleware

import cats.Functor
import cats.data.Kleisli

object URITranslation {
  def translateRoot[F[_]: Functor, G[_]: Functor, B](prefix: String)(
    @deprecatedName('service) http: Kleisli[F, Request[G], B]): Kleisli[F, Request[G], B] = {
    val newCaret = prefix match {
      case "/" => 0
      case x if x.startsWith("/") => x.length
      case x => x.length + 1
    }

    Kleisli{ req =>
      val oldCaret = req.attributes
        .get(Request.Keys.PathInfoCaret)
        .getOrElse(0)
      http(req.withAttribute(Request.Keys.PathInfoCaret(oldCaret + newCaret)))
    }
  }
}
