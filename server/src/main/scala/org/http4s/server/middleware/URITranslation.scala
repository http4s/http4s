package org.http4s
package server
package middleware

import cats.{Applicative, Functor}
import cats.data.{Kleisli, OptionT}

object URITranslation {
  def translateRoot[F[_]: Applicative](prefix: String)(service: HttpService[F]): HttpService[F] =
    prefix match {
      case "" | "/" => service
      case prefix if prefix.startsWith("/") =>
        val newCaret = prefix.length

        Kleisli { req: Request[F] =>
          if (req.pathInfo.startsWith(prefix)) service(setCaret(req, newCaret))
          else OptionT.none
        }
      case prefix =>
        val newCaret = prefix.length + 1

        Kleisli { req: Request[F] =>
          val pi = req.pathInfo
          val shouldTranslate =
            if (pi.startsWith("/")) pi.substring(1).startsWith(prefix)
            else pi.startsWith(prefix)
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
