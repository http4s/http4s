/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server
package middleware

import cats.data.Kleisli
import cats._
import cats.syntax.all._
import org.http4s._

object ErrorHandling {
  def apply[F[_], G[_]](k: Kleisli[F, Request[G], Response[G]])(implicit
      F: MonadError[F, Throwable]): Kleisli[F, Request[G], Response[G]] =
    Kleisli { req =>
      val pf: PartialFunction[Throwable, F[Response[G]]] =
        inDefaultServiceErrorHandler[F, G](F)(req)
      k.run(req).handleErrorWith { e =>
        pf.lift(e) match {
          case Some(resp) => resp
          case None => F.raiseError(e)
        }
      }
    }

  def httpRoutes[F[_]: MonadError[*[_], Throwable]](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(httpRoutes)

  def httpApp[F[_]: MonadError[*[_], Throwable]](httpApp: HttpApp[F]): HttpApp[F] =
    apply(httpApp)
}
