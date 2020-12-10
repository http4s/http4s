/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.staticcontent

import cats.effect.IO
import org.http4s._
import org.http4s.Method.GET
import org.http4s.syntax.all._

class WebjarServiceFilterSuite extends Http4sSuite with StaticContentShared {
  def routes: HttpRoutes[IO] =
    webjarServiceBuilder[IO]
      .withWebjarAssetFilter(webjar =>
        webjar.library == "test-lib" && webjar.version == "1.0.0" && webjar.asset == "testresource.txt")
      .toRoutes

  test("Return a 200 Ok file") {
    val req = Request[IO](GET, uri"/test-lib/1.0.0/testresource.txt")
    val rb = runReq(req)

    rb.flatMap { case (b, r) =>
      assertEquals(r.status, Status.Ok)
      b.assertEquals(testWebjarResource)
    }
  }

  test("Not find filtered asset") {
    val req = Request[IO](GET, uri"/test-lib/1.0.0/sub/testresource.txt")
    val rb = runReq(req)

    rb.flatMap { case (_, r) =>
      IO.pure(r.status).assertEquals(Status.NotFound)
    }
  }
}
