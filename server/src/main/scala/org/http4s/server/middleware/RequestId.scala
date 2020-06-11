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
import cats.effect.Sync
import cats.implicits._
import org.typelevel.ci.CIString
import java.util.UUID

/** Propagate a `X-Request-Id` header to the response, generate a UUID
  * when the `X-Request-Id` header is unset.
  * https://devcenter.heroku.com/articles/http-request-id
  */
object RequestId {

  private[this] val requestIdHeader = CIString("X-Request-ID")

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
        response <- http(req.putHeaders(header))
      } yield response.putHeaders(header)
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
        response <- http(req.putHeaders(header))
      } yield response.putHeaders(header)
    }

  def httpApp[F[_]: Sync](httpApp: HttpApp[F]): HttpApp[F] =
    apply(requestIdHeader)(httpApp)

  def httpApp[F[_]: Sync](
      headerName: CIString
  )(httpApp: HttpApp[F]): HttpApp[F] =
    apply(headerName)(httpApp)

  def httpApp[F[_]: Sync](
      headerName: CIString = requestIdHeader,
      genReqId: F[UUID]
  )(httpApp: HttpApp[F]): HttpApp[F] =
    apply(FunctionK.id[F], headerName, genReqId)(httpApp)

  def httpRoutes[F[_]: Sync](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(requestIdHeader)(httpRoutes)

  def httpRoutes[F[_]: Sync](
      headerName: CIString
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(headerName)(httpRoutes)

  def httpRoutes[F[_]: Sync](
      headerName: CIString = requestIdHeader,
      genReqId: F[UUID]
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(OptionT.liftK[F], headerName, genReqId)(httpRoutes)
}
