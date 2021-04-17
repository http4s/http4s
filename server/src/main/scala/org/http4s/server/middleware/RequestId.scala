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

import cats.{FlatMap, ~>}
import cats.data.{Kleisli, NonEmptyList, OptionT}
import cats.effect.{IO, Sync}
import cats.mtl._
import cats.syntax.all._
import org.typelevel.ci._
import org.typelevel.vault.Key
import java.util.UUID

/** Propagate a `X-Request-Id` header to the response, generate a UUID
  * when the `X-Request-Id` header is unset.
  * https://devcenter.heroku.com/articles/http-request-id
  */
object RequestId {

  private[this] val requestIdHeader = ci"X-Request-ID"

  val requestIdAttrKey: Key[String] = Key.newKey[IO, String].unsafeRunSync()

  def apply[G[_], F[_]](
      http: G[Response[F]])(implicit G: Sync[G], L: Local[G, Request[F]]): G[Response[F]] =
    apply(requestIdHeader)(http)

  def apply[G[_], F[_]](
      headerName: CIString
  )(http: G[Response[F]])(implicit G: Sync[G], L: Local[G, Request[F]]): G[Response[F]] =
    for {
      req <- L.ask
      header <- req.headers.get(headerName).map(_.head) match {
        case None => G.delay(Header.Raw(headerName, UUID.randomUUID().toString()))
        case Some(header) => G.pure(header)
      }
      reqId = header.value
      resp <- L.local(http)(_.withAttribute(requestIdAttrKey, reqId).putHeaders(header))
    } yield resp.withAttribute(requestIdAttrKey, reqId).putHeaders(header)

  def apply[G[_], F[_]](
      fk: F ~> G,
      headerName: CIString = requestIdHeader,
      genReqId: F[UUID]
  )(http: G[Response[F]])(implicit G: FlatMap[G], L: Local[G, Request[F]], F: Sync[F]): G[Response[F]] =
    for {
      req <- L.ask
      header <- fk(req.headers.get(headerName) match {
        case None => genReqId.map(reqId => Header.Raw(headerName, reqId.show))
        case Some(NonEmptyList(header, _)) => F.pure(header)
      })
      reqId = header.value
      resp <- L.local(http)(_.withAttribute(requestIdAttrKey, reqId).putHeaders(header))
    } yield resp.withAttribute(requestIdAttrKey, reqId).putHeaders(header)

  object httpApp {
    def apply[F[_]: Sync](httpApp: HttpApp[F]): HttpApp[F] =
      RequestId.apply(requestIdHeader)(httpApp)

    def apply[F[_]: Sync](
        headerName: CIString
    )(httpApp: HttpApp[F]): HttpApp[F] =
      RequestId.apply(headerName)(httpApp)

    def apply[F[_]: Sync](
        headerName: CIString = requestIdHeader,
        genReqId: F[UUID]
    )(httpApp: HttpApp[F]): HttpApp[F] =
      RequestId.apply(Kleisli.liftK[F, Request[F]], headerName, genReqId)(httpApp)
  }

  object httpRoutes {
    def apply[F[_]: Sync](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
      RequestId.apply(requestIdHeader)(httpRoutes)

    def apply[F[_]: Sync](
        headerName: CIString
    )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
      RequestId.apply(headerName)(httpRoutes)

    def apply[F[_]: Sync](
        headerName: CIString = requestIdHeader,
        genReqId: F[UUID]
    )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
      RequestId.apply(OptionT.liftK.andThen(Kleisli.liftK[OptionT[F, *], Request[F]]), headerName, genReqId)(httpRoutes)
  }
}
