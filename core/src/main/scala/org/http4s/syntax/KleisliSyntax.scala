/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package syntax

import cats.{Functor, ~>}
import cats.syntax.functor._
import cats.data.{Kleisli, OptionT}
import cats.Defer

trait KleisliSyntax {
  implicit def http4sKleisliResponseSyntaxOptionT[F[_]: Functor, A](
      kleisli: Kleisli[OptionT[F, *], A, Response[F]]): KleisliResponseOps[F, A] =
    new KleisliResponseOps[F, A](kleisli)
}

trait KleisliSyntaxBinCompat0 {
  implicit def http4sKleisliHttpRoutesSyntax[F[_]: Functor](
      routes: HttpRoutes[F]): KleisliHttpRoutesOps[F] =
    new KleisliHttpRoutesOps[F](routes)

  implicit def http4sKleisliHttpAppSyntax[F[_]: Functor](app: HttpApp[F]): KleisliHttpAppOps[F] =
    new KleisliHttpAppOps[F](app)
}

trait KleisliSyntaxBinCompat1 {
  implicit def http4sKleisliAuthedRoutesSyntax[F[_]: Functor, A](
      authedRoutes: AuthedRoutes[A, F]): KleisliAuthedRoutesOps[F, A] =
    new KleisliAuthedRoutesOps[F, A](authedRoutes)
}

final class KleisliResponseOps[F[_]: Functor, A](self: Kleisli[OptionT[F, *], A, Response[F]]) {
  def orNotFound: Kleisli[F, A, Response[F]] =
    Kleisli(a => self.run(a).getOrElse(Response.notFound))
}

final class KleisliHttpRoutesOps[F[_]: Functor](self: HttpRoutes[F]) {
  def translate[G[_]: Defer: Functor](fk: F ~> G)(gK: G ~> F): HttpRoutes[G] =
    HttpRoutes(request => self.run(request.mapK(gK)).mapK(fk).map(_.mapK(fk)))
}

final class KleisliHttpAppOps[F[_]: Functor](self: HttpApp[F]) {
  def translate[G[_]: Defer](fk: F ~> G)(gK: G ~> F): HttpApp[G] =
    HttpApp(request => fk(self.run(request.mapK(gK)).map(_.mapK(fk))))
}

final class KleisliAuthedRoutesOps[F[_]: Functor, A](self: AuthedRoutes[A, F]) {
  def translate[G[_]: Defer: Functor](fk: F ~> G)(gK: G ~> F): AuthedRoutes[A, G] =
    AuthedRoutes(authedReq => self.run(authedReq.mapK(gK)).mapK(fk).map(_.mapK(fk)))
}
