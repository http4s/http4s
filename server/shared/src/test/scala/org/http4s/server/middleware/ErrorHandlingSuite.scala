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

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.syntax.all._

class ErrorHandlingSuite extends Http4sSuite {
  private def routes(t: Throwable) =
    HttpRoutes.of[IO] { case GET -> Root / "error" =>
      IO.raiseError(t)
    }

  private val request = Request[IO](GET, uri"/error")

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
