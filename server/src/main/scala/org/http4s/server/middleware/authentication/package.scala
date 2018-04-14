package org.http4s
package server
package middleware

import cats.data.{Kleisli, OptionT}
import cats.effect._
import org.http4s.headers._

package object authentication {
  def challenged[F[_], A](
      challenge: Kleisli[F, Request[F], Either[Challenge, AuthedRequest[F, A]]])(
      @deprecatedName('service) routes: AuthedService[A, F])(implicit F: Sync[F]): HttpRoutes[F] =
    Kleisli { req =>
      challenge
        .mapF(OptionT.liftF(_))
        .run(req)
        .flatMap {
          case Right(authedRequest) =>
            routes(authedRequest)
          case Left(challenge) =>
            OptionT.some(Response(Status.Unauthorized).putHeaders(`WWW-Authenticate`(challenge)))
        }
    }
}
