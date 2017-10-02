package org.http4s
package server
package middleware

import cats._
import cats.data.Kleisli
import cats.implicits._
import org.http4s.instances.kleisli._

/** Handles HEAD requests as a GET without a body.
  *
  * If the service returns the fallthrough response, the request is resubmitted
  * as a GET.  The resulting response's body is killed, but all headers are
  * preserved.  This is a naive, but correct, implementation of HEAD.  Routes
  * requiring more optimization should implement their own HEAD handler.
  */
object DefaultHead {
  def apply[F[_]: Monad](service: HttpService[F]): HttpService[F] =
    Kleisli { req =>
      req.method match {
        case Method.HEAD =>
          (service <+> headAsTruncatedGet(service))(req)
        case _ =>
          service(req)
      }
    }

  private def headAsTruncatedGet[F[_]: Functor](service: HttpService[F]): HttpService[F] =
    Kleisli { req =>
      val getReq = req.withMethod(Method.GET)
      service(getReq).map(response => response.copy(body = response.body.drain))
    }
}
