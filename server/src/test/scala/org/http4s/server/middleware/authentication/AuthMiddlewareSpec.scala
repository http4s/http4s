package org.http4s.server.middleware.authentication

import cats.data.{EitherT, Kleisli, OptionT}
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
        Kleisli.liftF(OptionT.none)

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
        Kleisli.liftF(OptionT.none)

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
        Kleisli.liftF(OptionT.none)

      val authedService: AuthedService[User, IO] =
        AuthedService {
          case POST -> Root as _ => Ok()
        }

      val regularRoutes: HttpRoutes[IO] = HttpRoutes.pure(Response[IO](Ok))

      val middleware = AuthMiddleware(authUser)

      val service = middleware(authedService)

      (service <+> regularRoutes).orNotFound(Request[IO](method = Method.POST)) must returnStatus(
        Unauthorized)
      (service <+> regularRoutes).orNotFound(Request[IO](method = Method.GET)) must returnStatus(
        Unauthorized)
    }

    "not consume the entire request when using fall through" in {

      val authUser: Kleisli[OptionT[IO, ?], Request[IO], User] =
        Kleisli.liftF(OptionT.none)

      val authedService: AuthedService[User, IO] =
        AuthedService {
          case POST -> Root as _ => Ok()
        }

      val regularRoutes: HttpRoutes[IO] = HttpRoutes.of {
        case GET -> _ => Ok()
      }

      val middleware = AuthMiddleware.withFallThrough(authUser)

      val service = middleware(authedService)

      //Unauthenticated
      (service <+> regularRoutes).orNotFound(Request[IO](method = Method.POST)) must returnStatus(
        NotFound)
      //Matched normally
      (service <+> regularRoutes).orNotFound(Request[IO](method = Method.GET)) must returnStatus(Ok)
      //Unmatched
      (service <+> regularRoutes).orNotFound(Request[IO](method = Method.PUT)) must returnStatus(
        NotFound)

    }

    "return 'error' response for EitherT.left for noSpiderWithEither" in {

      import org.http4s.util.CaseInsensitiveString

      sealed trait Error
      case object NoAuth extends Error
      case object NoPathMatch extends Error

      val authUserWithError: Kleisli[EitherT[IO, Error, ?], Request[IO], User] = Kleisli {
        case r @ GET -> Root / "hello" =>
          if (r.headers.toList.map(_.name).contains[CaseInsensitiveString](CaseInsensitiveString("authorization")))
            EitherT.right[Error](IO.pure(42))
          else
            EitherT.left[User](IO.pure(NoAuth))
        case _ =>
          EitherT.left[User](IO.pure(NoPathMatch))
      }

      val authedService: AuthedService[User, IO] =
        AuthedService {
          case GET -> Root / "hello" as _ => Ok()
        }

      def onError(request: Request[IO], error: Error): IO[Response[IO]] =
        error match {
          case NoAuth => IO.pure(Response(status = Unauthorized))
          case NoPathMatch => IO.pure(Response(status = ServiceUnavailable))
        }

      val middleware = AuthMiddleware.noSpiderWithEither(authUserWithError, onError)

      val service = middleware(authedService)

      //Unauthenticated but path matches
      service.orNotFound(Request[IO](method = Method.GET, uri = Uri.unsafeFromString("hello"))) must returnStatus(Unauthorized)
      //Matched normally
      service.orNotFound(Request[IO](method = Method.GET, uri = Uri.unsafeFromString("hello"), headers = Headers.of((Header("Authorization", "..."))))) must returnStatus(Ok)
      //Unauthenticated and path does not match
      service.orNotFound(Request[IO](method = Method.GET, uri = Uri.unsafeFromString("not-matched"))) must returnStatus(ServiceUnavailable)

    }

  }

}
