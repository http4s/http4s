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

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.Sync
import org.http4s.headers._

package object authentication {
  // TODO Could be reduced to a Monad[F]
  def challenged[F[_], A](
      challenge: Kleisli[F, Request[F], Either[Challenge, AuthedRequest[F, A]]]
  )(routes: AuthedRoutes[A, F])(implicit F: Sync[F]): HttpRoutes[F] =
    Kleisli { req =>
      OptionT[F, Response[F]] {
        F.flatMap(challenge(req)) {
          case Left(challenge) => F.pure(Some(unauthorized(challenge)))
          case Right(authedRequest) => routes(authedRequest).value
        }
      }
    }

  private[this] def unauthorized[F[_]](challenge: Challenge): Response[F] =
    Response(Status.Unauthorized).putHeaders(`WWW-Authenticate`(challenge))
}
