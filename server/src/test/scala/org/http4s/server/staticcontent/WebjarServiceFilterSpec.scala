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

package org.http4s.server.staticcontent

import cats.effect.IO
import org.http4s._
import org.http4s.Method.GET
import org.http4s.server.staticcontent.WebjarService.Config

object WebjarServiceFilterSpec extends Http4sSpec with StaticContentShared {
  def routes: HttpRoutes[IO] =
    webjarService(
      Config(
        filter = webjar =>
          webjar.library == "test-lib" && webjar.version == "1.0.0" && webjar.asset == "testresource.txt",
        blocker = testBlocker
      )
    )

  "The WebjarService" should {
    "Return a 200 Ok file" in {
      val req = Request[IO](GET, Uri(path = "/test-lib/1.0.0/testresource.txt"))
      val rb = runReq(req)

      rb._1 must_== testWebjarResource
      rb._2.status must_== Status.Ok
    }

    "Not find filtered asset" in {
      val req = Request[IO](GET, Uri(path = "/test-lib/1.0.0/sub/testresource.txt"))
      val rb = runReq(req)

      rb._2.status must_== Status.NotFound
    }
  }
}
