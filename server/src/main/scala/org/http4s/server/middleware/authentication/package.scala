package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import org.http4s.headers._

package object authentication {
  def challenged[F[_], A](challenge: Service[F, Request[F], Either[Challenge, AuthedRequest[F, A]]])
                         (service: AuthedService[F, A])
                         (implicit F: Sync[F]): HttpService[F] =
    Service.lift { req =>
      challenge(req).flatMap {
        case Right(authedRequest) =>
          service(authedRequest)
        case Left(challenge) =>
          F.pure(Response[F](Status.Unauthorized).putHeaders(`WWW-Authenticate`(challenge)))
      }
    }
}
