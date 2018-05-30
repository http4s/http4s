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
  def apply[F[_], G[_], B](@deprecatedName('service) http: Kleisli[F, Request[G], B])(
    implicit F: MonoidK[F],
    G: Functor[G]): Kleisli[F, Request[G], B] = {
    Kleisli { req =>
      http(req) <+> {
        req.method match {
          case Method.HEAD => {
            http(req.withMethod(Method.GET).withEmptyBody)
          }
          case _ => http(req) 
        }
      }
    }
  }
}
