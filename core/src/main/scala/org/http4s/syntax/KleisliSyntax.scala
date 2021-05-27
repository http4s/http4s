/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package syntax

import cats.{Functor, Monad, ~>}
import cats.syntax.functor._
import cats.data.{Kleisli, OptionT}

trait KleisliSyntax {
  implicit def http4sKleisliResponseSyntaxOptionT[F[_]: Functor, A](
      kleisli: Kleisli[OptionT[F, *], A, Response[F]]): KleisliResponseOps[F, A] =
    new KleisliResponseOps[F, A](kleisli)

  implicit def http4sKleisliHttpRoutesSyntax[F[_]](routes: HttpRoutes[F]): KleisliHttpRoutesOps[F] =
    new KleisliHttpRoutesOps[F](routes)

  implicit def http4sKleisliHttpAppSyntax[F[_]: Functor](app: HttpApp[F]): KleisliHttpAppOps[F] =
    new KleisliHttpAppOps[F](app)

  implicit def http4sKleisliAuthedRoutesSyntax[F[_], A](
      authedRoutes: AuthedRoutes[A, F]): KleisliAuthedRoutesOps[F, A] =
    new KleisliAuthedRoutesOps[F, A](authedRoutes)
}

final class KleisliResponseOps[F[_]: Functor, A](self: Kleisli[OptionT[F, *], A, Response[F]]) {
  def orNotFound: Kleisli[F, A, Response[F]] =
    Kleisli(a => self.run(a).getOrElse(Response.notFound))
}

final class KleisliHttpRoutesOps[F[_]](self: HttpRoutes[F]) {
  def translate[G[_]: Monad](fk: F ~> G)(gK: G ~> F): HttpRoutes[G] =
    HttpRoutes(request => self.run(request.mapK(gK)).mapK(fk).map(_.mapK(fk)))
}

final class KleisliHttpAppOps[F[_]: Functor](self: HttpApp[F]) {
  def translate[G[_]: Monad](fk: F ~> G)(gK: G ~> F): HttpApp[G] =
    HttpApp(request => fk(self.run(request.mapK(gK)).map(_.mapK(fk))))
}

final class KleisliAuthedRoutesOps[F[_], A](self: AuthedRoutes[A, F]) {
  def translate[G[_]: Monad](fk: F ~> G)(gK: G ~> F): AuthedRoutes[A, G] =
    AuthedRoutes(authedReq => self.run(authedReq.mapK(gK)).mapK(fk).map(_.mapK(fk)))
}
