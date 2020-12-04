/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import cats.syntax.all._
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.syntax.all._
import org.http4s.Uri.{Authority, RegName, Scheme}

class HttpsRedirectSuite extends Http4sSuite {
  val innerRoutes = HttpRoutes.of[IO] { case GET -> Root =>
    Ok("pong")
  }

  val reqHeaders = Headers.of(Header("X-Forwarded-Proto", "http"), Header("Host", "example.com"))
  val req = Request[IO](method = GET, uri = Uri(path = Uri.Path.Root), headers = reqHeaders)

  test("redirect to https when 'X-Forwarded-Proto' is http") {
    List(
      HttpsRedirect(innerRoutes).orNotFound,
      HttpsRedirect.httpRoutes(innerRoutes).orNotFound,
      HttpsRedirect.httpApp(innerRoutes.orNotFound)
    ).traverse { app =>
      val expectedAuthority = Authority(host = RegName("example.com"))
      val expectedLocation =
        Location(
          Uri(
            path = Uri.Path.Root,
            scheme = Some(Scheme.https),
            authority = Some(expectedAuthority)))
      val expectedHeaders = Headers(expectedLocation :: `Content-Type`(MediaType.text.xml) :: Nil)
      app(req).map(_.status).assertEquals(Status.MovedPermanently) *>
        app(req).map(_.headers).assertEquals(expectedHeaders)
    }
  }

  test("not redirect otherwise") {
    List(
      HttpsRedirect(innerRoutes).orNotFound,
      HttpsRedirect.httpRoutes(innerRoutes).orNotFound,
      HttpsRedirect.httpApp(innerRoutes.orNotFound)
    ).traverse { app =>
      val noHeadersReq = Request[IO](method = GET, uri = Uri(path = Uri.Path.Root))
      app(noHeadersReq).map(_.status).assertEquals(Status.Ok) *>
        app(noHeadersReq).flatMap(_.as[String]).assertEquals("pong")
    }
  }
}
