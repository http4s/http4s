/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats.effect._
import org.http4s._
import org.http4s.syntax.all._
import org.http4s.dsl.io._
import org.http4s.Uri.uri

class StaticHeadersSuite extends Http4sSuite {
  val testService = HttpRoutes.of[IO] {
    case GET -> Root / "request" =>
      Ok("request response")
    case req @ POST -> Root / "post" =>
      Ok(req.body)
  }

  test("add a no-cache header to a response") {
    val req = Request[IO](uri = uri("/request"))
    val resp = StaticHeaders.`no-cache`(testService).orNotFound(req)

    resp
      .map(_.headers.toList.map(_.toString).contains("Cache-Control: no-cache"))
      .assertEquals(true)
  }
}
