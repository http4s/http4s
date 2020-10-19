/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s.testing.Http4sLegacyMatchersIO

class HttpsRedirectSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  val innerRoutes = HttpRoutes.of[IO] { case GET -> Root =>
    Ok("pong")
  }

  val reqHeaders = Headers.of(Header("X-Forwarded-Proto", "http"), Header("Host", "example.com"))
  val req = Request[IO](method = GET, uri = Uri(path = Uri.Path.Root), headers = reqHeaders)

  "HttpsRedirect" should {
    "redirect to https when 'X-Forwarded-Proto' is http" in {
      List(
        HttpsRedirect(innerRoutes).orNotFound,
        HttpsRedirect.httpRoutes(innerRoutes).orNotFound,
        HttpsRedirect.httpApp(innerRoutes.orNotFound)
      ).map { app =>
        val resp = app(req).unsafeRunSync()
        val expectedAuthority = Authority(host = RegName("example.com"))
        val expectedLocation =
          Location(
            Uri(
              path = Uri.Path.Root,
              scheme = Some(Scheme.https),
              authority = Some(expectedAuthority)))
        val expectedHeaders = Headers(expectedLocation :: `Content-Type`(MediaType.text.xml) :: Nil)
        resp.status must_== Status.MovedPermanently
        resp.headers must_== expectedHeaders
      }
    }

    "not redirect otherwise" in {
      List(
        HttpsRedirect(innerRoutes).orNotFound,
        HttpsRedirect.httpRoutes(innerRoutes).orNotFound,
        HttpsRedirect.httpApp(innerRoutes.orNotFound)
      ).map { app =>
        val noHeadersReq = Request[IO](method = GET, uri = uri"/")
        val resp = app(noHeadersReq).unsafeRunSync()
        resp.status must_== Status.Ok
        resp.as[String] must returnValue("pong")
      }
    }
  }
}
