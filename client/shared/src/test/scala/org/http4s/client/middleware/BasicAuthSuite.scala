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

import cats.effect.IO
import org.http4s.Method.GET
import org.http4s.Uri.Path.Root
import org.http4s.client.dsl.io._
import org.http4s.dsl.io._
import org.http4s.server.middleware.authentication.{BasicAuth => ServerBasicAuth}
import org.http4s.syntax.literals._

class BasicAuthSuite extends Http4sSuite {

  private val validCredentials = BasicCredentials("username", "password")

  private val service = {
    val authStore = (incomingCredentials: BasicCredentials) =>
      if (incomingCredentials == validCredentials)
        IO.some(incomingCredentials.username)
      else
        IO.none
    val basicAuth = ServerBasicAuth("test-realm", authStore)
    basicAuth(
      AuthedRoutes.of[String, IO] {
        case GET -> Root / "protected" as user => Ok(s"Logged in as $user")
        case _ => NotFound()
      }
    ).orNotFound
  }

  private val client = Client.fromHttpApp[IO](service)

  private val protectedEndpoint = GET(uri"/protected")

  test("Client without provided credentials should fail with 401") {
    val authClient = BasicAuth(None)(client)
    authClient.status(protectedEndpoint).assertEquals(Status.Unauthorized)
  }

  test("Client with invalid basic auth should fail with 401") {
    val wrongCredentials = BasicCredentials("xxx", "xxx")
    val authClient = BasicAuth(wrongCredentials)(client)
    authClient.status(protectedEndpoint).assertEquals(Status.Unauthorized)
  }

  test("Client with valid basic auth should succeed") {
    val authClient = BasicAuth(validCredentials)(client)
    authClient.status(protectedEndpoint).assertEquals(Status.Ok)
  }

}
