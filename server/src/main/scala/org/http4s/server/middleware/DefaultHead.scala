package org.http4s
package server
package middleware

import cats._
import cats.data.Kleisli
import cats.implicits._

/** Handles HEAD requests as a GET without a body.
  *
  * If the service returns the fallthrough response, the request is resubmitted
  * as a GET.  The resulting response's body is killed, but all headers are
  * preserved.  This is a naive, but correct, implementation of HEAD.  Routes
  * requiring more optimization should implement their own HEAD handler.
  */
object DefaultHead {
  def apply[F[_]: Functor, G[_]](@deprecatedName('service) http: Http[F, G])(
      implicit F: MonoidK[F]): Http[F, G] =
    Kleisli { req =>
      req.method match {
        case Method.HEAD => {
          (http <+> headAsTruncatedGet(http))(req)
        }
        case _ => {
          http(req)
        }
      }
    }

  private def headAsTruncatedGet[F[_]: Functor, G[_]](http: Http[F, G]): Http[F, G] =
    Kleisli { req =>
      val getReq = req.withMethod(Method.GET)
      http(getReq).map(response => response.copy(body = response.body.drain))
    }
}
