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
package client
package middleware

import cats.effect._
import cats.syntax.all._
import org.http4s.dsl.io._
import org.http4s.server.middleware.authentication.DigestAuth
import org.http4s.syntax.all._

class DigestClientSpec extends Http4sSuite {

  val realm = "Test Server Realm"
  val login = "user"
  val password = "password"

  private val plainTextAuthStore: DigestAuth.AuthStore[IO, String] =
    DigestAuth.PlainTextAuthStore[IO, String]((u: String) =>
      IO.pure {
        if (u === login) Some(u -> password)
        else None
      }
    )

  private val server = AuthedRoutes.of[String, IO] {
    case GET -> Root as user => Ok(s"Hello, $user!")
    case GET -> Root / "unauthorized" as _ => Response[IO](Status.Unauthorized).pure[IO]
  }

  private val digestAuthMiddlewareIO = DigestAuth.applyF[IO, String](realm, plainTextAuthStore)

  test("DigestClient should return success response for correct auth") {
    val req = Request[IO](uri = uri"/")

    (for {
      mdlwr <- digestAuthMiddlewareIO
      app = mdlwr(server).orNotFound
      client = DigestClient(login, password)(Client.fromHttpApp(app))
      res <- client.expect[String](req)
    } yield res).assertEquals(s"Hello, $login!")

  }

  test("auth failed response for client with wrong auth") {
    val req = Request[IO](uri = uri"/")

    (for {
      mdlwr <- digestAuthMiddlewareIO
      app = mdlwr(server).orNotFound
      client = DigestClient(login + "wrong", password)(Client.fromHttpApp(app))
      res <- client.status(req)
    } yield res).assertEquals(Status.Unauthorized)
  }

  test("unauthorized response when server reply unauthorized") {
    val req = Request[IO](uri = uri"/unauthorized")

    (for {
      mdlwr <- digestAuthMiddlewareIO
      app = mdlwr(server).orNotFound
      client = DigestClient(login, password)(Client.fromHttpApp(app))
      res <- client.status(req)
    } yield res).assertEquals(Status.Unauthorized)
  }

}
