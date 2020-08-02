/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package middleware

import cats.effect._
import org.http4s.dsl.io._
import org.http4s.server.middleware.authentication.DigestAuth
import org.http4s.testing.Http4sLegacyMatchersIO

class DigestClientSpec extends Http4sSpec with Http4sLegacyMatchersIO {

  val realm = "Test Server Realm"
  val login = "login"
  val password = "password"

  def authStore(u: String): IO[Option[(String, String)]] =
    IO(if (u == login) Some(u -> password) else None)

  val digestAuthMiddleware = DigestAuth(realm, authStore)

  val server = digestAuthMiddleware(AuthedRoutes.of[String, IO] {
    case GET -> Root / "request" as user =>
      Ok(s"hello: $user")
    case GET -> Root / "unauthorized" as _ =>
      IO.pure(Response[IO](Status.Unauthorized))
  })

  val defaultClient: Client[IO] = Client.fromHttpApp(server.orNotFound)
  val digestClient: Client[IO] =
    DigestClient(login, password)(defaultClient)
  val wrongAuthDigestClient: Client[IO] =
    DigestClient(login + "wrong", password)(defaultClient)

  "DigestClient" should {
    "success response for correct auth" in {
      val req = Request[IO](uri = uri"/request")
      val resp = digestClient.expect[String](req).unsafeRunSync()
      resp must_== s"hello: $login"
    }

    "auth failed response for client with wrong auth" in {
      val req = Request[IO](uri = uri"/request")
      val resp = wrongAuthDigestClient.status(req).unsafeRunSync()
      resp must_== Status.Unauthorized
    }

    "unauthorized response when server reply unauthorized" in {
      val req = Request[IO](uri = uri"/unauthorized")
      val resp = digestClient.status(req).unsafeRunSync()
      resp must_== Status.Unauthorized
    }

  }
}
