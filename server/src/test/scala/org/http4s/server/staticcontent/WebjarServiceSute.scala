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
package server
package staticcontent

import cats.effect.IO
import java.nio.file.Paths
import org.http4s.Method.{GET, POST}
import org.http4s.Uri.uri
import org.http4s.syntax.all._
import org.http4s.server.staticcontent.WebjarService.Config

class WebjarServiceSuite extends Http4sSuite with StaticContentShared {
  def routes: HttpRoutes[IO] =
    webjarService(Config[IO](blocker = testBlocker))
  val defaultBase =
    org.http4s.server.test.BuildInfo.test_resourceDirectory.toPath
      .resolve("META-INF/resources/webjars")
      .toString

  test("Return a 200 Ok file") {
    val req = Request[IO](GET, Uri(path = "/test-lib/1.0.0/testresource.txt"))
    val rb = runReq(req)
    rb.flatMap { case (b, r) =>
      assertEquals(r.status, Status.Ok)
      b.assertEquals(testWebjarResource)
    }
  }

  test("Return a 200 Ok file in a subdirectory") {
    val req = Request[IO](GET, Uri(path = "/test-lib/1.0.0/sub/testresource.txt"))
    val rb = runReq(req)

    rb.flatMap { case (b, r) =>
      assertEquals(r.status, Status.Ok)
      b.assertEquals(testWebjarSubResource)
    }
  }

  test("Decodes path segments") {
    val req = Request[IO](uri = uri("/deep+purple/machine+head/space+truckin%27.txt"))
    routes.orNotFound(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Return a 400 on a relative link even if it's inside the context") {
    val relativePath = "test-lib/1.0.0/sub/../testresource.txt"
    val file = Paths.get(defaultBase).resolve(relativePath).toFile

    val uri = Uri.unsafeFromString("/" + relativePath)
    val req = Request[IO](uri = uri)
    IO(file.exists()).assertEquals(true) *>
      routes.orNotFound(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test("Return a 400 if the request tries to escape the context") {
    val relativePath = "../../../testresource.txt"
    val file = Paths.get(defaultBase).resolve(relativePath).toFile

    val uri = Uri.unsafeFromString("/" + relativePath)
    val req = Request[IO](uri = uri)
    IO(file.exists()).assertEquals(true) *>
      routes.orNotFound(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test("Return a 400 if the request tries to escape the context with /") {
    val absPath = Paths.get(defaultBase).resolve("test-lib/1.0.0/testresource.txt")
    val file = absPath.toFile

    val uri = Uri.unsafeFromString("///" + absPath)
    val req = Request[IO](uri = uri)
    IO(file.exists()).assertEquals(true) *>
      routes.orNotFound(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test("Not find missing file") {
    val req = Request[IO](uri = uri("/test-lib/1.0.0/doesnotexist.txt"))
    routes.apply(req).value.assertEquals(Option.empty[Response[IO]])
  }

  test("Not find missing library") {
    val req = Request[IO](uri = uri("/1.0.0/doesnotexist.txt"))
    routes.apply(req).value.assertEquals(Option.empty[Response[IO]])
  }

  test("Return bad request on missing version") {
    val req = Request[IO](uri = uri("/test-lib//doesnotexist.txt"))
    routes.orNotFound(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test("Not find blank asset") {
    val req = Request[IO](uri = uri("/test-lib/1.0.0/"))
    routes.apply(req).value.assertEquals(Option.empty[Response[IO]])
  }

  test("Not match a request with POST") {
    val req = Request[IO](POST, Uri(path = "/test-lib/1.0.0/testresource.txt"))
    routes.apply(req).value.assertEquals(Option.empty[Response[IO]])
  }
}
