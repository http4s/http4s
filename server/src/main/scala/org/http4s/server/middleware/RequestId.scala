/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import org.http4s.{Header, Http, Request, Response}
import cats.data.Kleisli
import cats.effect.Sync
import cats.implicits._
import org.typelevel.ci.CIString
import java.util.UUID

/** Propagate a `X-Request-Id` header to the response, generate a UUID
  * when the `X-Request-Id` header is unset.
  * https://devcenter.heroku.com/articles/http-request-id
  */
object RequestId {

  def apply[G[_], F[_]](headerName: CIString = CIString("X-Request-ID"))(http: Http[G, F])(implicit
      G: Sync[G]): Http[G, F] =
    Kleisli[G, Request[F], Response[F]] { req =>
      for {
        header <- req.headers.get(headerName) match {
          case None => G.delay[Header](Header.Raw(headerName, UUID.randomUUID().show))
          case Some(header) => G.pure[Header](header)
        }
        response <- http(req.putHeaders(header))
      } yield response.putHeaders(header)
    }
}
