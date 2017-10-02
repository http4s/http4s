package org.http4s.server.middleware.authentication

import cats.data.{Kleisli, OptionT}
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.AuthMiddleware

class AuthMiddlewareSpec extends Http4sSpec {

  type User = Long

  "AuthMiddleware" should {
    "fall back to onAuthFailure when authentication returns a Either.Left" in {

      val authUser: Kleisli[IO, Request[IO], Either[String, User]] =
        Kleisli.pure(Left("Unauthorized"))

      val onAuthFailure: AuthedService[IO, String] =
        Kleisli(req => OptionT.liftF(Forbidden(req.authInfo)))

      val authedService: AuthedService[IO, User] =
        AuthedService {
          case _ => Ok()
        }

      val middleWare = AuthMiddleware(authUser, onAuthFailure)

      val service = middleWare(authedService)

      service.orNotFound(Request[IO]()) must returnStatus(Forbidden)
      service.orNotFound(Request[IO]()) must returnBody("Unauthorized")
    }

    "enrich the request with a user when authentication returns Either.Right" in {

      val userId: User = 42

      val authUser: Kleisli[IO, Request[IO], Either[String, User]] =
        Kleisli.pure(Right(userId))

      val onAuthFailure: AuthedService[IO, String] =
        Kleisli(req => OptionT.liftF(Forbidden(req.authInfo)))

      val authedService: AuthedService[IO, User] =
        AuthedService {
          case GET -> Root as user => Ok(user.toString)
        }

      val middleWare = AuthMiddleware(authUser, onAuthFailure)

      val service = middleWare(authedService)

      service.orNotFound(Request[IO]()) must returnStatus(Ok)
      service.orNotFound(Request[IO]()) must returnBody("42")
    }

    "not find a route if requested with the wrong verb inside an authenticated route" in {
      val userId: User = 42

      val authUser: Kleisli[IO, Request[IO], Either[String, User]] =
        Kleisli.pure(Right(userId))

      val onAuthFailure: AuthedService[IO, String] =
        Kleisli(req => OptionT.liftF(Forbidden(req.authInfo)))

      val authedService: AuthedService[IO, User] =
        AuthedService {
          case POST -> Root as _ => Ok()
        }

      val middleWare = AuthMiddleware(authUser, onAuthFailure)

      val service = middleWare(authedService)

      service.orNotFound(Request[IO](method = Method.POST)) must returnStatus(Ok)
      service.orNotFound(Request[IO](method = Method.GET)) must returnStatus(NotFound)
    }
  }

}
