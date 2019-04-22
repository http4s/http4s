package org.http4s.server.middleware

import org.http4s.{Http, Method}
import cats.{Functor, MonoidK}
import cats.syntax.functor._
import cats.syntax.semigroupk._

/** Handles HEAD requests as a GET without a body.
  *
  * If the service returns the fallthrough response, the request is resubmitted
  * as a GET.  The resulting response's body is killed, but all headers are
  * preserved.  This is a naive, but correct, implementation of HEAD.  Routes
  * requiring more optimization should implement their own HEAD handler.
  */
object DefaultHead {
  def apply[F[_]: Functor, G[_]](http: Http[F, G])(implicit F: MonoidK[F]): Http[F, G] =
    req =>
      req.method match {
        case Method.HEAD => http(req) <+> headAsTruncatedGet(http).apply(req)
        case _           => http(req)
      }

  private def headAsTruncatedGet[F[_]: Functor, G[_]](http: Http[F, G]): Http[F, G] =
    req => {
      val getReq = req.withMethod(Method.GET)
      http(getReq).map(response => response.copy(body = response.body.drain))
    }
}
