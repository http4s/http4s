package org.http4s.server.middleware.authentication

import cats.data.{Kleisli, OptionT}
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.AuthMiddleware
import cats.syntax.semigroupk._

class AuthMiddlewareSpec extends Http4sSpec {

  type User = Long

  "AuthMiddleware" should {
    "fall back to onAuthFailure when authentication returns a Either.Left" in {

      val authUser: Kleisli[IO, Request[IO], Either[String, User]] =
        Kleisli.pure(Left("Unauthorized"))

      val onAuthFailure: AuthedService[String, IO] =
        Kleisli(req => OptionT.liftF(Forbidden(req.authInfo)))

      val authedService: AuthedService[User, IO] =
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

      val onAuthFailure: AuthedService[String, IO] =
        Kleisli(req => OptionT.liftF(Forbidden(req.authInfo)))

      val authedService: AuthedService[User, IO] =
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

      val onAuthFailure: AuthedService[String, IO] =
        Kleisli(req => OptionT.liftF(Forbidden(req.authInfo)))

      val authedService: AuthedService[User, IO] =
        AuthedService {
          case POST -> Root as _ => Ok()
        }

      val middleWare = AuthMiddleware(authUser, onAuthFailure)

      val service = middleWare(authedService)

      service.orNotFound(Request[IO](method = Method.POST)) must returnStatus(Ok)
      service.orNotFound(Request[IO](method = Method.GET)) must returnStatus(NotFound)
    }

    "return 200 for a matched and authenticated route" in {
      val userId: User = 42

      val authUser: Kleisli[OptionT[IO, ?], Request[IO], User] =
        Kleisli.pure(userId)

      val authedService: AuthedService[User, IO] =
        AuthedService {
          case POST -> Root as _ => Ok()
        }

      val middleware = AuthMiddleware(authUser)

      val service = middleware(authedService)

      service.orNotFound(Request[IO](method = Method.POST)) must returnStatus(Ok)
    }

    "return 404 for an unmatched but authenticated route" in {
      val userId: User = 42

      val authUser: Kleisli[OptionT[IO, ?], Request[IO], User] =
        Kleisli.pure(userId)

      val authedService: AuthedService[User, IO] =
        AuthedService {
          case POST -> Root as _ => Ok()
        }

      val middleware = AuthMiddleware(authUser)

      val service = middleware(authedService)

      service.orNotFound(Request[IO](method = Method.GET)) must returnStatus(NotFound)
    }

    "return 401 for a matched, but unauthenticated route" in {
      val authUser: Kleisli[OptionT[IO, ?], Request[IO], User] =
        Kleisli.lift(OptionT.none)

      val authedService: AuthedService[User, IO] =
        AuthedService {
          case POST -> Root as _ => Ok()
        }

      val middleware = AuthMiddleware(authUser)

      val service = middleware(authedService)

      service.orNotFound(Request[IO](method = Method.POST)) must returnStatus(Unauthorized)
    }

    "return 401 for an unmatched, unauthenticated route" in {
      val authUser: Kleisli[OptionT[IO, ?], Request[IO], User] =
        Kleisli.lift(OptionT.none)

      val authedService: AuthedService[User, IO] =
        AuthedService {
          case POST -> Root as _ => Ok()
        }

      val middleware = AuthMiddleware(authUser)

      val service = middleware(authedService)

      service.orNotFound(Request[IO](method = Method.GET)) must returnStatus(Unauthorized)
    }

    "compose authedServices and not fall through" in {
      val userId: User = 42

      val authUser: Kleisli[OptionT[IO, ?], Request[IO], User] =
        Kleisli.pure(userId)

      val authedService1: AuthedService[User, IO] =
        AuthedService {
          case POST -> Root as _ => Ok()
        }

      val authedService2: AuthedService[User, IO] =
        AuthedService {
          case GET -> Root as _ => Ok()
        }

      val middleware = AuthMiddleware(authUser)

      val service = middleware(authedService1 <+> authedService2)

      service.orNotFound(Request[IO](method = Method.GET)) must returnStatus(Ok)
      service.orNotFound(Request[IO](method = Method.POST)) must returnStatus(Ok)
    }

    "consume the entire request for an unauthenticated route for service composition" in {
      val authUser: Kleisli[OptionT[IO, ?], Request[IO], User] =
        Kleisli.lift(OptionT.none)

      val authedService: AuthedService[User, IO] =
        AuthedService {
          case POST -> Root as _ => Ok()
        }

      val regularService: HttpService[IO] = HttpService[IO] {
        case GET -> Root => Ok()
      }

      val middleware = AuthMiddleware(authUser)

      val service = middleware(authedService)

      (service <+> regularService).orNotFound(Request[IO](method = Method.POST)) must returnStatus(
        Unauthorized)
      (service <+> regularService).orNotFound(Request[IO](method = Method.GET)) must returnStatus(
        Unauthorized)
    }

  }

}
