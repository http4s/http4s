package org.http4s.server
package middleware

import cats.data.Kleisli
import cats._
import cats.implicits._
import org.http4s._

object ErrorHandling {

  def apply[F[_], G[_], A](k: Kleisli[F, Request[G], Response[G]])(
      implicit F: MonadError[F, Throwable],
      G: Applicative[G]): Kleisli[F, Request[G], Response[G]] =
    Kleisli { req =>
      val pf: PartialFunction[Throwable, F[Response[G]]] = inDefaultServiceErrorHandler[F, G](F, G)(req)
      k.run(req).handleErrorWith { e =>
        pf.lift(e) match {
          case Some(resp)=> resp
          case None => F.raiseError(e)
        }
      }
    }

}
