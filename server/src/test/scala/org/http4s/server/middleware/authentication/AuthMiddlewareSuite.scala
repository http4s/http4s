/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware.authentication

import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.http4s.server.AuthMiddleware

class AuthMiddlewareSuite extends Http4sSuite {
  type User = Long

  test("fall back to onAuthFailure when authentication returns a Either.Left") {
    val authUser: Kleisli[IO, Request[IO], Either[String, User]] =
      Kleisli.pure(Left("Unauthorized"))

    val onAuthFailure: AuthedRoutes[String, IO] =
      Kleisli(req => OptionT.liftF(Forbidden(req.context)))

    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case _ =>
        Ok()
      }

    val middleWare = AuthMiddleware(authUser, onAuthFailure)

    val service = middleWare(authedRoutes)

    service
      .orNotFound(Request[IO]())
      .flatMap { res =>
        res.as[String].map {
          _ === "Unauthorized" && res.status === Forbidden
        }
      }
      .assertEquals(true)
  }

  test("enrich the request with a user when authentication returns Either.Right") {
    val userId: User = 42

    val authUser: Kleisli[IO, Request[IO], Either[String, User]] =
      Kleisli.pure(Right(userId))

    val onAuthFailure: AuthedRoutes[String, IO] =
      Kleisli(req => OptionT.liftF(Forbidden(req.context)))

    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case GET -> Root as user =>
        Ok(user.toString)
      }

    val middleWare = AuthMiddleware(authUser, onAuthFailure)

    val service = middleWare(authedRoutes)

    service
      .orNotFound(Request[IO]())
      .flatMap { res =>
        res.as[String].map {
          _ === "42" && res.status === Ok
        }
      }
      .assertEquals(true)
  }

  test("not find a route if requested with the wrong verb inside an authenticated route") {
    val userId: User = 42

    val authUser: Kleisli[IO, Request[IO], Either[String, User]] =
      Kleisli.pure(Right(userId))

    val onAuthFailure: AuthedRoutes[String, IO] =
      Kleisli(req => OptionT.liftF(Forbidden(req.context)))

    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val middleWare = AuthMiddleware(authUser, onAuthFailure)

    val service = middleWare(authedRoutes)

    service
      .orNotFound(Request[IO](method = Method.POST))
      .map(_.status)
      .assertEquals(Ok) *>
      service
        .orNotFound(Request[IO](method = Method.GET))
        .map(_.status)
        .assertEquals(NotFound)
  }

  test("return 200 for a matched and authenticated route") {
    val userId: User = 42

    val authUser: Kleisli[OptionT[IO, *], Request[IO], User] =
      Kleisli.pure(userId)

    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val middleware = AuthMiddleware(authUser)

    val service = middleware(authedRoutes)

    service.orNotFound(Request[IO](method = Method.POST)).map(_.status).assertEquals(Ok)
  }

  test("return 404 for an unmatched but authenticated route") {
    val userId: User = 42

    val authUser: Kleisli[OptionT[IO, *], Request[IO], User] =
      Kleisli.pure(userId)

    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val middleware = AuthMiddleware(authUser)

    val service = middleware(authedRoutes)

    service.orNotFound(Request[IO](method = Method.GET)).map(_.status).assertEquals(NotFound)
  }

  test("return 401 for a matched, but unauthenticated route") {
    val authUser: Kleisli[OptionT[IO, *], Request[IO], User] =
      Kleisli.liftF(OptionT.none)

    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val middleware = AuthMiddleware(authUser)

    val service = middleware(authedRoutes)

    service.orNotFound(Request[IO](method = Method.POST)).map(_.status).assertEquals(Unauthorized)
  }

  test("return 401 for an unmatched, unauthenticated route") {
    val authUser: Kleisli[OptionT[IO, *], Request[IO], User] =
      Kleisli.liftF(OptionT.none)

    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val middleware = AuthMiddleware(authUser)

    val service = middleware(authedRoutes)

    service.orNotFound(Request[IO](method = Method.GET)).map(_.status).assertEquals(Unauthorized)
  }

  test("compose authedRoutesand not fall through") {
    val userId: User = 42

    val authUser: Kleisli[OptionT[IO, *], Request[IO], User] =
      Kleisli.pure(userId)

    val authedRoutes1: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val authedRoutes2: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case GET -> Root as _ =>
        Ok()
      }

    val middleware = AuthMiddleware(authUser)

    val service = middleware(authedRoutes1 <+> authedRoutes2)

    service.orNotFound(Request[IO](method = Method.GET)).map(_.status).assertEquals(Ok) *>
      service.orNotFound(Request[IO](method = Method.POST)).map(_.status).assertEquals(Ok)
  }

  test("consume the entire request for an unauthenticated route for service composition") {
    val authUser: Kleisli[OptionT[IO, *], Request[IO], User] =
      Kleisli.liftF(OptionT.none)

    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val regularRoutes: HttpRoutes[IO] = HttpRoutes.pure(Response[IO](Ok))

    val middleware = AuthMiddleware(authUser)

    val service = middleware(authedRoutes)

    (service <+> regularRoutes)
      .orNotFound(Request[IO](method = Method.POST))
      .map(_.status)
      .assertEquals(Unauthorized) *>
      (service <+> regularRoutes)
        .orNotFound(Request[IO](method = Method.GET))
        .map(_.status)
        .assertEquals(Unauthorized)
  }

  test("not consume the entire request when using fall through") {
    val authUser: Kleisli[OptionT[IO, *], Request[IO], User] =
      Kleisli.liftF(OptionT.none)

    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val regularRoutes: HttpRoutes[IO] = HttpRoutes.of { case GET -> _ =>
      Ok()
    }

    val middleware = AuthMiddleware.withFallThrough(authUser)

    val service = middleware(authedRoutes)

    //Unauthenticated
    (service <+> regularRoutes)
      .orNotFound(Request[IO](method = Method.POST))
      .map(_.status)
      .assertEquals(NotFound) *>
      //Matched normally
      (service <+> regularRoutes)
        .orNotFound(Request[IO](method = Method.GET))
        .map(_.status)
        .assertEquals(Ok) *>
      //Unmatched
      (service <+> regularRoutes)
        .orNotFound(Request[IO](method = Method.PUT))
        .map(_.status)
        .assertEquals(NotFound)
  }
}
