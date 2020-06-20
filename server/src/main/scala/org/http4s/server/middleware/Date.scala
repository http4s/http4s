/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats._
import cats.data.Kleisli
import cats.implicits._
import cats.effect._

import org.http4s._
import org.http4s.headers.{Date => HDate}

/**
  * Date Middleware, adds the Date Header to All Responses generated
  * by the service.
 **/
object Date {
  def apply[G[_]: Monad: Clock, F[_], A](
      k: Kleisli[G, A, Response[F]]): Kleisli[G, A, Response[F]] =
    Kleisli { a =>
      for {
        resp <- k(a)
        header <-
          resp.headers
            .get(HDate)
            .fold(
              HttpDate.current[G].map(HDate(_))
            )(_.pure[G])
      } yield resp.putHeaders(header)
    }

  def httpRoutes[F[_]: Monad: Clock](routes: HttpRoutes[F]): HttpRoutes[F] =
    apply(routes)

  def httpApp[F[_]: Monad: Clock](app: HttpApp[F]): HttpApp[F] =
    apply(app)
}
