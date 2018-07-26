package org.http4s
package server
package middleware

import cats.{Applicative, Functor}
import cats.data.{Kleisli, OptionT}

object URITranslation {
  def translateRoot[F[_]: Applicative](prefix: String)(service: HttpService[F]): HttpService[F] =
    if (prefix.isEmpty || prefix == "/") service
    else {
      val (slashedPrefix, unslashedPrefix) =
        if (prefix.startsWith("/")) (prefix, prefix.substring(1))
        else (s"/$prefix", prefix)

      val newCaret = slashedPrefix.length

      Kleisli { req: Request[F] =>
        val shouldTranslate =
          req.pathInfo.startsWith(unslashedPrefix) || req.pathInfo.startsWith(slashedPrefix)

        if (shouldTranslate) service(setCaret(req, newCaret))
        else OptionT.none
      }

    }

  private def setCaret[F[_]: Functor](req: Request[F], newCaret: Int): Request[F] = {
    val oldCaret = req.attributes
      .get(Request.Keys.PathInfoCaret)
      .getOrElse(0)
    req.withAttribute(Request.Keys.PathInfoCaret(oldCaret + newCaret))
  }
}
