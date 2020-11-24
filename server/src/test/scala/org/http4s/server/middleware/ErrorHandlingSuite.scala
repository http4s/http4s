/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats.effect.IO
import org.http4s._
import org.http4s.Uri.uri
import org.http4s.dsl.io._
import org.http4s.syntax.all._

class ErrorHandlingSuite extends Http4sSuite {
  def routes(t: Throwable) =
    HttpRoutes.of[IO] { case GET -> Root / "error" =>
      IO.raiseError(t)
    }

  val request = Request[IO](GET, uri("/error"))

  test("Handle errors based on the default service error handler") {
    ErrorHandling(
      routes(ParseFailure("Error!", "Error details"))
    ).orNotFound(request).map(_.status).assertEquals(Status.BadRequest)
  }

  test("Be created via the httpRoutes constructor") {
    ErrorHandling
      .httpRoutes(
        routes(ParseFailure("Error!", "Error details"))
      )
      .orNotFound(request)
      .map(_.status)
      .assertEquals(Status.BadRequest)
  }

  test("Be created via the httpApp constructor") {
    ErrorHandling
      .httpApp(
        routes(ParseFailure("Error!", "Error details")).orNotFound
      )
      .apply(request)
      .map(_.status)
      .assertEquals(Status.BadRequest)
  }
}
