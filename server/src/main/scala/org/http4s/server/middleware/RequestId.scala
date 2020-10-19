/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

package server
package middleware

import org.http4s.{Header, Http, Request, Response}
import cats.{FlatMap, ~>}
import cats.arrow.FunctionK
import cats.data.{Kleisli, OptionT}
import cats.effect.{IO, Sync}
import cats.implicits._
import org.typelevel.ci.CIString
import io.chrisdavenport.vault.Key
import java.util.UUID

/** Propagate a `X-Request-Id` header to the response, generate a UUID
  * when the `X-Request-Id` header is unset.
  * https://devcenter.heroku.com/articles/http-request-id
  */
object RequestId {

  private[this] val requestIdHeader = CIString("X-Request-ID")

  val requestIdAttrKey: Key[String] = Key.newKey[IO, String].unsafeRunSync()

  def apply[G[_], F[_]](http: Http[G, F])(implicit G: Sync[G]): Http[G, F] =
    apply(requestIdHeader)(http)

  def apply[G[_], F[_]](
      headerName: CIString
  )(http: Http[G, F])(implicit G: Sync[G]): Http[G, F] =
    Kleisli[G, Request[F], Response[F]] { req =>
      for {
        header <- req.headers.get(headerName) match {
          case None => G.delay(Header.Raw(headerName, UUID.randomUUID().toString()))
          case Some(header) => G.pure[Header](header)
        }
        reqId = header.value
        response <- http(req.withAttribute(requestIdAttrKey, reqId).putHeaders(header))
      } yield response.withAttribute(requestIdAttrKey, reqId).putHeaders(header)
    }

  def apply[G[_], F[_]](
      fk: F ~> G,
      headerName: CIString = requestIdHeader,
      genReqId: F[UUID]
  )(http: Http[G, F])(implicit G: FlatMap[G], F: Sync[F]): Http[G, F] =
    Kleisli[G, Request[F], Response[F]] { req =>
      for {
        header <- fk(req.headers.get(headerName) match {
          case None => genReqId.map(reqId => Header.Raw(headerName, reqId.show))
          case Some(header) => F.pure[Header](header)
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
        genReqId: F[UUID]
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
        genReqId: F[UUID]
    )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
      RequestId.apply(OptionT.liftK[F], headerName, genReqId)(httpRoutes)
  }
}
