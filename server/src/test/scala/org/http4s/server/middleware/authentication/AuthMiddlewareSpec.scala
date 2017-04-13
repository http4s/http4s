package org.http4s.server.middleware.authentication

import cats.data.Kleisli
import fs2.Task
import org.http4s.server.AuthMiddleware
import org.http4s._
import org.http4s.dsl._

class AuthMiddlewareSpec extends Http4sSpec {

  type User = Long

  "AuthMiddleware" should {
    "fall back to onAuthFailure when authentication returns a Either.Left" in {

      val authUser: Service[Request, Either[String, User]] =
        Kleisli(_ => Task.now(Left("Unauthorized")))

      val onAuthFailure: AuthedService[String] =
        Kleisli(req => Forbidden(req.authInfo))

      val authedService: AuthedService[User] =
        AuthedService {
          case _ => Ok()
        }

      val middleWare = AuthMiddleware(authUser, onAuthFailure)

      val service = middleWare(authedService)

      service.orNotFound(Request()) must returnStatus(Forbidden)
      service.orNotFound(Request()) must returnBody("Unauthorized")
    }

    "enrich the request with a user when authentication returns Either.Right" in {

      val userId: User = 42

      val authUser: Service[Request, Either[String, User]] =
        Kleisli(_ => Task.now(Right(userId)))

      val onAuthFailure: AuthedService[String] =
        Kleisli(req => Forbidden(req.authInfo))

      val authedService: AuthedService[User] =
        AuthedService {
          case GET -> Root as user => Ok(user.toString)
        }

      val middleWare = AuthMiddleware(authUser, onAuthFailure)

      val service = middleWare(authedService)

      service.orNotFound(Request()) must returnStatus(Ok)
      service.orNotFound(Request()) must returnBody("42")
    }
    "not find a route if requested with the wrong verb inside an authenticated route" in {
      val userId: User = 42

      val authUser: Service[Request, Either[String, User]] =
        Kleisli(_ => Task.now(Right(userId)))

      val onAuthFailure: AuthedService[String] =
        Kleisli(req => Forbidden(req.authInfo))

      val authedService: AuthedService[User] =
        AuthedService {
          case POST -> Root as user => Ok()
        }

      val middleWare = AuthMiddleware(authUser, onAuthFailure)

      val service = middleWare(authedService)

      service.orNotFound(Request(method = Method.POST)) must returnStatus(Ok)
      service.orNotFound(Request(method = Method.GET)) must returnStatus(NotFound)
    }
  }

}
