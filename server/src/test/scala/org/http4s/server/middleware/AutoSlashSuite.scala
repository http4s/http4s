/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats.effect._
import org.http4s.Uri.uri
import org.http4s.syntax.all._
import org.http4s.server.{MockRoute, Router}
import org.http4s.{Http4sSuite, HttpRoutes, Request, Status}

class AutoSlashSuite extends Http4sSuite {
  val route = MockRoute.route()

  val pingRoutes = {
    import org.http4s.dsl.io._
    HttpRoutes.of[IO] { case GET -> Root / "ping" =>
      Ok()
    }
  }

  test("Auto remove a trailing slash") {
    val req = Request[IO](uri = uri("/ping/"))
    route.orNotFound(req).map(_.status).assertEquals(Status.NotFound) *>
      AutoSlash(route).orNotFound(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Match a route defined with a slash") {
    AutoSlash(route)
      .orNotFound(Request[IO](uri = uri("/withslash")))
      .map(_.status)
      .assertEquals(Status.Ok) *>
      AutoSlash(route)
        .orNotFound(Request[IO](uri = uri("/withslash/")))
        .map(_.status)
        .assertEquals(Status.Accepted)
  }

  test("Respect an absent trailing slash") {
    val req = Request[IO](uri = uri("/ping"))
    route.orNotFound(req).map(_.status).assertEquals(Status.Ok)
    AutoSlash(route).orNotFound(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Not crash on empty path") {
    val req = Request[IO](uri = uri(""))
    AutoSlash(route).orNotFound(req).map(_.status).assertEquals(Status.NotFound)
  }

  test("Work when nested in Router") {
    // See https://github.com/http4s/http4s/issues/1378
    val router = Router("/public" -> AutoSlash(pingRoutes))
    router
      .orNotFound(Request[IO](uri = uri("/public/ping")))
      .map(_.status)
      .assertEquals(Status.Ok) *>
      router
        .orNotFound(Request[IO](uri = uri("/public/ping/")))
        .map(_.status)
        .assertEquals(Status.Ok)
  }

  test("Work when Router is nested in AutoSlash") {
    // See https://github.com/http4s/http4s/issues/1947
    val router = AutoSlash(Router("/public" -> pingRoutes))
    router
      .orNotFound(Request[IO](uri = uri("/public/ping")))
      .map(_.status)
      .assertEquals(Status.Ok) *>
      router
        .orNotFound(Request[IO](uri = uri("/public/ping/")))
        .map(_.status)
        .assertEquals(Status.Ok)
  }

  test("Be created via httpRoutes constructor") {
    val req = Request[IO](uri = uri("/ping/"))
    AutoSlash.httpRoutes(route).orNotFound(req).map(_.status).assertEquals(Status.Ok)
  }
}
