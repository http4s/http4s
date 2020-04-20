package org.http4s
package server
package middleware

import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import org.http4s.headers._
import org.http4s.implicits._

package object authentication {
  def challenged[F[_], A](
      challenge: Kleisli[F, Request[F], Either[Challenge, AuthedRequest[F, A]]])(
      routes: AuthedRoutes[A, F])(implicit F: Sync[F]): HttpRoutes[F] =
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
