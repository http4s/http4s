/*
 * Copyright 2014 http4s.org
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

package server
package middleware

import cats.FlatMap
import cats.Monad
import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.Sync
import cats.effect.SyncIO
import cats.effect.std.UUIDGen
import cats.syntax.all._
import cats.~>
import org.typelevel.ci._
import org.typelevel.vault.Key

import java.util.UUID

/** Propagate a `X-Request-Id` header to the response, generate a UUID
  * when the `X-Request-Id` header is unset.
  * https://devcenter.heroku.com/articles/http-request-id
  */
object RequestId {

  private[this] val requestIdHeader = ci"X-Request-ID"

  val requestIdAttrKey: Key[String] = Key.newKey[SyncIO, String].unsafeRunSync()

  @deprecated("Preserved for bincompat", "0.23.31")
  def apply[G[_], F[_]](http: Http[G, F], G: Sync[G]): Http[G, F] =
    apply(requestIdHeader)(http)(G, UUIDGen.fromSync(G))

  def apply[G[_]: Monad: UUIDGen, F[_]](http: Http[G, F]): Http[G, F] =
    apply(requestIdHeader)(http)

  @deprecated("Preserved for bincompat", "0.23.12")
  def apply[G[_], F[_]](headerName: CIString, http: Http[G, F], G: Sync[G]): Http[G, F] =
    apply(headerName)(http)(G, UUIDGen.fromSync(G))

  @deprecated("Preserved for bincompat", "0.23.31")
  def apply[G[_], F[_]](
      headerName: CIString,
      http: Http[G, F],
      G: Sync[G],
      gen: UUIDGen[G],
  ): Http[G, F] =
    apply(headerName)(http)(G, gen)

  def apply[G[_]: Monad: UUIDGen, F[_]](
      headerName: CIString
  )(http: Http[G, F]): Http[G, F] =
    apply(headerName, UUIDGen.randomUUID[G])(http)

  @deprecated("Preserved for bincompat", "0.23.31")
  def apply[G[_], F[_]](
      fk: F ~> G,
      headerName: CIString,
      genReqId: F[UUID],
      http: Http[G, F],
      G: FlatMap[G],
      F: Sync[F],
  ): Http[G, F] = {
    implicit val monadForG: Monad[G] = new Monad[G] {
      def pure[A](a: A): G[A] = fk(F.pure(a))

      def flatMap[A, B](fa: G[A])(f: A => G[B]): G[B] = G.flatMap(fa)(f)

      def tailRecM[A, B](a: A)(f: A => G[Either[A, B]]): G[B] =
        G.tailRecM(a)(f)
    }

    apply(headerName, fk(genReqId))(http)
  }

  def apply[G[_]: Monad, F[_]](
      fk: F ~> G,
      headerName: CIString = requestIdHeader,
      genReqId: F[UUID],
  )(http: Http[G, F]): Http[G, F] =
    apply(headerName, fk(genReqId))(http)

  def apply[G[_], F[_]](headerName: CIString, genReqId: G[UUID])(
      http: Http[G, F]
  )(implicit G: Monad[G]): Http[G, F] =
    Kleisli[G, Request[F], Response[F]] { req =>
      for {
        header <- req.headers
          .get(headerName)
          .map(_.head)
          .fold(genReqId.map(reqId => Header.Raw(headerName, reqId.show)))(G.pure)
        reqId = header.value
        response <- http(req.withAttribute(requestIdAttrKey, reqId).putHeaders(header))
      } yield response.withAttribute(requestIdAttrKey, reqId).putHeaders(header)
    }

  object httpApp {
    @deprecated("Preserved for bincompat", "0.23.31")
    def apply[F[_]](httpApp: HttpApp[F], F: Sync[F]): HttpApp[F] =
      apply(httpApp)(F, UUIDGen.fromSync(F))

    def apply[F[_]: Monad: UUIDGen](httpApp: HttpApp[F]): HttpApp[F] =
      RequestId.apply(requestIdHeader)(httpApp)

    @deprecated("Preserved for bincompat", "0.23.31")
    def apply[F[_]](headerName: CIString, httpApp: HttpApp[F], F: Sync[F]): HttpApp[F] =
      apply(headerName)(httpApp)(F, UUIDGen.fromSync(F))

    def apply[F[_]: Monad: UUIDGen](
        headerName: CIString
    )(httpApp: HttpApp[F]): HttpApp[F] =
      RequestId.apply(headerName)(httpApp)

    @deprecated("Preserved for bincompat", "0.23.31")
    def apply[F[_]](
        headerName: CIString,
        genReqId: F[UUID],
        httpApp: HttpApp[F],
        F: Sync[F],
    ): HttpApp[F] =
      apply(headerName, genReqId)(httpApp)(F)

    def apply[F[_]: Monad](
        headerName: CIString = requestIdHeader,
        genReqId: F[UUID],
    )(httpApp: HttpApp[F]): HttpApp[F] =
      RequestId.apply(FunctionK.id[F], headerName, genReqId)(httpApp)
  }

  object httpRoutes {
    @deprecated("Preserved for bincompat", "0.23.31")
    def apply[F[_]](httpRoutes: HttpRoutes[F], F: Sync[F]): HttpRoutes[F] =
      apply(httpRoutes)(F, UUIDGen.fromSync(F))

    def apply[F[_]: Monad: UUIDGen](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
      RequestId.apply(requestIdHeader)(httpRoutes)

    @deprecated("Preserved for bincompat", "0.23.31")
    def apply[F[_]](headerName: CIString, httpRoutes: HttpRoutes[F], F: Sync[F]): HttpRoutes[F] =
      apply(headerName)(httpRoutes)(F, UUIDGen.fromSync(F))

    def apply[F[_]: Monad: UUIDGen](
        headerName: CIString
    )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
      RequestId.apply(headerName)(httpRoutes)

    @deprecated("Preserved for bincompat", "0.23.31")
    def apply[F[_]](
        headerName: CIString,
        genReqId: F[UUID],
        httpRoutes: HttpRoutes[F],
        F: Sync[F],
    ): HttpRoutes[F] =
      apply(headerName, genReqId)(httpRoutes)(F)

    def apply[F[_]: Monad](
        headerName: CIString = requestIdHeader,
        genReqId: F[UUID],
    )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
      RequestId.apply(OptionT.liftK[F], headerName, genReqId)(httpRoutes)
  }
}
