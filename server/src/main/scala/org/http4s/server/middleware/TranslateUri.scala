package org.http4s
package server.middleware

import cats.data.Kleisli
import cats.{Functor, MonoidK}

/** Removes the given prefix from the beginning of the path of the [[Request]].
  */
object TranslateUri {

  def apply[F[_], G[_], B](prefix: String)(http: Kleisli[F, Request[G], B])(
      implicit F: MonoidK[F],
      G: Functor[G]): Kleisli[F, Request[G], B] =
    if (prefix.isEmpty || prefix == "/") http
    else {
      val (slashedPrefix, unslashedPrefix) =
        if (prefix.startsWith("/")) (prefix, prefix.substring(1))
        else (s"/$prefix", prefix)

      val newCaret = slashedPrefix.length

      Kleisli { req: Request[G] =>
        val shouldTranslate =
          req.pathInfo.startsWith(unslashedPrefix) || req.pathInfo.startsWith(slashedPrefix)

        if (shouldTranslate) http(setCaret(req, newCaret))
        else F.empty
      }

    }

  private def setCaret[F[_]: Functor](req: Request[F], newCaret: Int): Request[F] = {
    val oldCaret = req.attributes
      .lookup(Request.Keys.PathInfoCaret)
      .getOrElse(0)
    req.withAttribute(Request.Keys.PathInfoCaret, oldCaret + newCaret)
  }
}
