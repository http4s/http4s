/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server
package middleware

import cats.data.{Kleisli, OptionT}
import cats._
import cats.implicits._
import org.http4s._

object ErrorAction {
  def apply[F[_]: ApplicativeError[*[_], Throwable], G[_], B](
      k: Kleisli[F, Request[G], B],
      f: (Request[G], Throwable) => F[Unit]
  ): Kleisli[F, Request[G], B] =
    Kleisli { req =>
      k.run(req).onError { case e => f(req, e) }
    }

  def log[F[_]: ApplicativeError[*[_], Throwable], G[_], B](
      http: Kleisli[F, Request[G], B],
      messageFailureLogAction: (Throwable, => String) => F[Unit],
      serviceErrorLogAction: (Throwable, => String) => F[Unit]
  ): Kleisli[F, Request[G], B] =
    apply(
      http,
      {
        case (req, mf: MessageFailure) =>
          messageFailureLogAction(
            mf,
            s"""Message failure handling request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
              .getOrElse("<unknown>")}"""
          )
        case (req, e) =>
          serviceErrorLogAction(
            e,
            s"""Error servicing request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
              .getOrElse("<unknown>")}"""
          )
      }
    )

  object httpApp {
    def apply[F[_]: ApplicativeError[*[_], Throwable]](
        httpApp: HttpApp[F],
        f: (Request[F], Throwable) => F[Unit]): HttpApp[F] =
      ErrorAction(httpApp, f)

    def log[F[_]: ApplicativeError[*[_], Throwable], G[_], B](
        httpApp: HttpApp[F],
        messageFailureLogAction: (Throwable, => String) => F[Unit],
        serviceErrorLogAction: (Throwable, => String) => F[Unit]
    ): HttpApp[F] =
      ErrorAction.log(httpApp, messageFailureLogAction, serviceErrorLogAction)
  }

  object httpRoutes {
    def apply[F[_]: MonadError[*[_], Throwable]](
        httpRoutes: HttpRoutes[F],
        f: (Request[F], Throwable) => F[Unit]): HttpRoutes[F] =
      ErrorAction(httpRoutes, liftFToOptionT(f))

    def log[F[_]: MonadError[*[_], Throwable]](
        httpRoutes: HttpRoutes[F],
        messageFailureLogAction: (Throwable, => String) => F[Unit],
        serviceErrorLogAction: (Throwable, => String) => F[Unit]
    ): HttpRoutes[F] =
      ErrorAction.log(
        httpRoutes,
        liftFToOptionT(messageFailureLogAction),
        liftFToOptionT(serviceErrorLogAction)
      )

    private def liftFToOptionT[F[_]: Functor, A, B, C](
        f: Function2[A, B, F[C]]): Function2[A, B, OptionT[F, C]] =
      (a: A, b: B) => OptionT.liftF(f(a, b))
  }
}
