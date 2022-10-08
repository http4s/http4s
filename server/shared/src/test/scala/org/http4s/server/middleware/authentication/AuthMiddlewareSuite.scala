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

package org.http4s.server.middleware.authentication

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.AuthMiddleware
import org.http4s.syntax.all._

class AuthMiddlewareSuite extends Http4sSuite {
  type User = Long

  private val userId42: User = 42

  private val authUser42: Request[IO] => IO[Option[User]] = _ => Some(userId42).pure[IO]

  private val noneShallPass: Request[IO] => IO[Option[User]] = _ => None.pure[IO]

  test("fall back to onAuthFailure when authentication returns a Either.Left") {
    val authUser: Request[IO] => IO[Either[String, User]] = _ => Left("Unauthorized").pure[IO]

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
      .assert
  }

  test("enrich the request with a user when authentication returns Either.Right") {

    val authUser: Request[IO] => IO[Either[String, User]] = _ => IO.pure(Right(userId42))

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
      .assert
  }

  test("not find a route if requested with the wrong verb inside an authenticated route") {
    val authUser: Request[IO] => IO[Either[String, User]] = _ => IO.pure(Right(userId42))

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
    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val middleware = AuthMiddleware(authUser42)

    val service = middleware(authedRoutes)

    service.orNotFound(Request[IO](method = Method.POST)).map(_.status).assertEquals(Ok)
  }

  test("return 404 for an unmatched but authenticated route") {
    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val middleware = AuthMiddleware(authUser42)

    val service = middleware(authedRoutes)

    service.orNotFound(Request[IO](method = Method.GET)).map(_.status).assertEquals(NotFound)
  }

  test("return 401 for a matched, but unauthenticated route") {
    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val middleware = AuthMiddleware(noneShallPass)

    val service = middleware(authedRoutes)

    service.orNotFound(Request[IO](method = Method.POST)).map(_.status).assertEquals(Unauthorized)
  }

  test("return 401 for an unmatched, unauthenticated route") {
    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val middleware = AuthMiddleware(noneShallPass)

    val service = middleware(authedRoutes)

    service.orNotFound(Request[IO](method = Method.GET)).map(_.status).assertEquals(Unauthorized)
  }

  test("compose authedRoutes and not fall through") {
    val authedRoutes1: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val authedRoutes2: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case GET -> Root as _ =>
        Ok()
      }

    val middleware = AuthMiddleware(authUser42)

    val service = middleware(authedRoutes1 <+> authedRoutes2)

    service.orNotFound(Request[IO](method = Method.GET)).map(_.status).assertEquals(Ok) *>
      service.orNotFound(Request[IO](method = Method.POST)).map(_.status).assertEquals(Ok)
  }

  test("consume the entire request for an unauthenticated route for service composition") {
    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ =>
        Ok()
      }

    val regularRoutes: HttpRoutes[IO] = HttpRoutes.pure(Response[IO](Ok))

    val middleware = AuthMiddleware(noneShallPass)

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

    val authedRoutes: AuthedRoutes[User, IO] =
      AuthedRoutes.of { case POST -> Root as _ => Ok() }

    val regularRoutes: HttpRoutes[IO] = HttpRoutes.of { case GET -> _ =>
      Ok()
    }

    val middleware = AuthMiddleware.withFallThrough(noneShallPass)

    val service = middleware(authedRoutes)

    // Unauthenticated
    (service <+> regularRoutes)
      .orNotFound(Request[IO](method = Method.POST))
      .map(_.status)
      .assertEquals(NotFound) *>
      // Matched normally
      (service <+> regularRoutes)
        .orNotFound(Request[IO](method = Method.GET))
        .map(_.status)
        .assertEquals(Ok) *>
      // Unmatched
      (service <+> regularRoutes)
        .orNotFound(Request[IO](method = Method.PUT))
        .map(_.status)
        .assertEquals(NotFound)
  }
}
