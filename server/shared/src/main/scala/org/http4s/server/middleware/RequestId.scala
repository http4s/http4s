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
import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.data.NonEmptyList
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
@org.typelevel.scalaccompat.annotation.nowarn
object RequestId {

  private[this] val requestIdHeader = ci"X-Request-ID"

  val requestIdAttrKey: Key[String] = Key.newKey[SyncIO, String].unsafeRunSync()

  def apply[G[_], F[_]](http: Http[G, F])(implicit G: Sync[G]): Http[G, F] =
    apply(requestIdHeader)(http)

  @deprecated("Preserved for bincompat", "0.23.12")
  def apply[G[_], F[_]](headerName: CIString, http: Http[G, F], G: Sync[G]): Http[G, F] =
    apply(headerName)(http)(G, UUIDGen.fromSync(G))

  def apply[G[_], F[_]](
      headerName: CIString
  )(http: Http[G, F])(implicit G: Sync[G], gen: UUIDGen[G]): Http[G, F] =
    Kleisli[G, Request[F], Response[F]] { req =>
      for {
        header <- req.headers.get(headerName).map(_.head) match {
          case None => UUIDGen.randomString[G].map(Header.Raw(headerName, _))
          case Some(header) => G.pure(header)
        }
        reqId = header.value
        response <- http(req.withAttribute(requestIdAttrKey, reqId).putHeaders(header))
      } yield response.withAttribute(requestIdAttrKey, reqId).putHeaders(header)
    }

  def apply[G[_], F[_]](
      fk: F ~> G,
      headerName: CIString = requestIdHeader,
      genReqId: F[UUID],
  )(http: Http[G, F])(implicit G: FlatMap[G], F: Sync[F]): Http[G, F] =
    Kleisli[G, Request[F], Response[F]] { req =>
      for {
        header <- fk(req.headers.get(headerName) match {
          case None => genReqId.map(reqId => Header.Raw(headerName, reqId.show))
          case Some(NonEmptyList(header, _)) => F.pure(header)
        })
        reqId = header.value
        response <- http(req.withAttribute(requestIdAttrKey, reqId).putHeaders(header))
      } yield response.withAttribute(requestIdAttrKey, reqId).putHeaders(header)
    }

  object httpApp {
    def apply[F[_]: Sync](httpApp: HttpApp[F]): HttpApp[F] =
      RequestId.apply(requestIdHeader)(httpApp)

    def apply[F[_]: Sync](
        headerName: CIString
    )(httpApp: HttpApp[F]): HttpApp[F] =
      RequestId.apply(headerName)(httpApp)

    def apply[F[_]: Sync](
        headerName: CIString = requestIdHeader,
        genReqId: F[UUID],
    )(httpApp: HttpApp[F]): HttpApp[F] =
      RequestId.apply(FunctionK.id[F], headerName, genReqId)(httpApp)
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
        genReqId: F[UUID],
    )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
      RequestId.apply(OptionT.liftK[F], headerName, genReqId)(httpRoutes)
  }
}
