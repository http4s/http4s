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

import cats.Functor
import cats.Monad
import cats.data.OptionT
import cats.syntax.functor._
import cats.~>

trait KleisliSyntax {

  implicit def http4sResponseSyntaxOptionT[F[_]: Functor, A](
      kleisli: Http[OptionT[F, *], F]
  ): HttpResponseOps[F, A] =
    new HttpResponseOps[F, A](kleisli)

  implicit def http4sHttpRoutesSyntax[F[_]](routes: HttpRoutes[F]): HttpRoutesOps[F] =
    new HttpRoutesOps[F](routes)

  implicit def http4sHttpAppSyntax[F[_]: Functor](app: HttpApp[F]): HttpAppOps[F] =
    new HttpAppOps[F](app)

  implicit def http4sAuthedRoutesSyntax[F[_], A](
      authedRoutes: AuthedRoutes[A, F]
  ): AuthedRoutesOps[F, A] =
    new AuthedRoutesOps[F, A](authedRoutes)
}

final class HttpResponseOps[F[_]: Functor, A](self: Http[OptionT[F, *], F]) {
  def orNotFound: Http[F, F] =
    a => self(a).getOrElse(Response.notFound)
}

final class HttpRoutesOps[F[_]](self: HttpRoutes[F]) {
  def translate[G[_]: Monad](fk: F ~> G)(gK: G ~> F): HttpRoutes[G] =
    HttpRoutes(request => self(request.mapK(gK)).mapK(fk).map(_.mapK(fk)))
}

final class HttpAppOps[F[_]: Functor](self: HttpApp[F]) {
  def translate[G[_]: Monad](fk: F ~> G)(gK: G ~> F): HttpApp[G] =
    HttpApp(request => fk(self(request.mapK(gK)).map(_.mapK(fk))))
}

final class AuthedRoutesOps[F[_], A](self: AuthedRoutes[A, F]) {
  def translate[G[_]: Monad](fk: F ~> G)(gK: G ~> F): AuthedRoutes[A, G] =
    AuthedRoutes(authedReq => self(authedReq.mapK(gK)).mapK(fk).map(_.mapK(fk)))
}
