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

package org.http4s.server.middleware

import cats.effect._
import org.http4s.Http4sSuite
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Status
import org.http4s.server.MockRoute
import org.http4s.server.Router
import org.http4s.syntax.all._

class AutoSlashSuite extends Http4sSuite {
  private val route = MockRoute.route()

  private val pingRoutes = {
    import org.http4s.dsl.io._
    HttpRoutes.of[IO] { case GET -> Root / "ping" =>
      Ok()
    }
  }

  test("Auto remove a trailing slash") {
    val req = Request[IO](uri = uri"/ping/")
    route.orNotFound(req).map(_.status).assertEquals(Status.NotFound) *>
      AutoSlash(route).orNotFound(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Match a route defined with a slash") {
    AutoSlash(route)
      .orNotFound(Request[IO](uri = uri"/withslash"))
      .map(_.status)
      .assertEquals(Status.Ok) *>
      AutoSlash(route)
        .orNotFound(Request[IO](uri = uri"/withslash/"))
        .map(_.status)
        .assertEquals(Status.Accepted)
  }

  test("Respect an absent trailing slash") {
    val req = Request[IO](uri = uri"/ping")
    route.orNotFound(req).map(_.status).assertEquals(Status.Ok)
    AutoSlash(route).orNotFound(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Not crash on empty path") {
    val req = Request[IO](uri = uri"")
    AutoSlash(route).orNotFound(req).map(_.status).assertEquals(Status.NotFound)
  }

  test("Work when nested in Router") {
    // See https://github.com/http4s/http4s/issues/1378
    val router = Router("/public" -> AutoSlash(pingRoutes))
    router
      .orNotFound(Request[IO](uri = uri"/public/ping"))
      .map(_.status)
      .assertEquals(Status.Ok) *>
      router
        .orNotFound(Request[IO](uri = uri"/public/ping/"))
        .map(_.status)
        .assertEquals(Status.Ok)
  }

  test("Work when Router is nested in AutoSlash") {
    // See https://github.com/http4s/http4s/issues/1947
    val router = AutoSlash(Router("/public" -> pingRoutes))
    router
      .orNotFound(Request[IO](uri = uri"/public/ping"))
      .map(_.status)
      .assertEquals(Status.Ok) *>
      router
        .orNotFound(Request[IO](uri = uri"/public/ping/"))
        .map(_.status)
        .assertEquals(Status.Ok)
  }

  test("Be created via httpRoutes constructor") {
    val req = Request[IO](uri = uri"/ping/")
    AutoSlash.httpRoutes(route).orNotFound(req).map(_.status).assertEquals(Status.Ok)
  }
}
