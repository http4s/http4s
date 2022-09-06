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
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Cache-Control`
import org.http4s.syntax.all._

class StaticHeadersSuite extends Http4sSuite {
  private val testService = HttpRoutes.of[IO] {
    case GET -> Root / "request" =>
      Ok("request response")
    case req @ POST -> Root / "post" =>
      Ok(req.body)
  }

  test("add a no-cache header to a response") {
    val req = Request[IO](uri = uri"/request")
    val resp = StaticHeaders.`no-cache`(testService).orNotFound(req)

    resp
      .map(_.headers.get[`Cache-Control`])
      .assertEquals(Some(`Cache-Control`(CacheDirective.`no-cache`())))
  }
}
