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
import org.http4s.testing.Http4sLegacyMatchersIO

class ErrorHandlingSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  def routes(t: Throwable) =
    HttpRoutes.of[IO] { case GET -> Root / "error" =>
      IO.raiseError(t)
    }

  val request = Request[IO](GET, uri("/error"))

  "ErrorHandling middleware" should {
    "Handle errors based on the default service error handler" in {
      ErrorHandling(
        routes(ParseFailure("Error!", "Error details"))
      ).orNotFound(request) must returnStatus(Status.BadRequest)
    }

    "Be created via the httpRoutes constructor" in {
      ErrorHandling
        .httpRoutes(
          routes(ParseFailure("Error!", "Error details"))
        )
        .orNotFound(request) must returnStatus(Status.BadRequest)
    }

    "Be created via the httpApp constructor" in {
      ErrorHandling
        .httpApp(
          routes(ParseFailure("Error!", "Error details")).orNotFound
        )
        .apply(request) must returnStatus(Status.BadRequest)
    }
  }
}
