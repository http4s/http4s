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
package middleware

import cats.effect._
import cats.syntax.all._
import org.http4s.Uri.Authority
import org.http4s.Uri.RegName
import org.http4s.Uri.Scheme
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.syntax.all._

class HttpsRedirectSuite extends Http4sSuite {
  private val innerRoutes = HttpRoutes.of[IO] { case GET -> Root =>
    Ok("pong")
  }

  private val reqHeaders = Headers("X-Forwarded-Proto" -> "http", Host("example.com"))
  private val req = Request[IO](method = GET, uri = Uri(path = Uri.Path.Root), headers = reqHeaders)

  test("redirect to https when 'X-Forwarded-Proto' is http") {
    List(
      HttpsRedirect(innerRoutes).orNotFound,
      HttpsRedirect.httpRoutes(innerRoutes).orNotFound,
      HttpsRedirect.httpApp(innerRoutes.orNotFound),
    ).parTraverse_ { app =>
      val expectedAuthority = Authority(host = RegName("example.com"))
      val expectedLocation =
        Location(
          Uri(
            path = Uri.Path.Root,
            scheme = Some(Scheme.https),
            authority = Some(expectedAuthority),
          )
        )
      val expectedHeaders = Headers(expectedLocation, `Content-Type`(MediaType.text.xml) :: Nil)
      app(req).map(_.status).assertEquals(Status.MovedPermanently) *>
        app(req).map(_.headers).assertEquals(expectedHeaders)
    }
  }

  test("not redirect otherwise") {
    List(
      HttpsRedirect(innerRoutes).orNotFound,
      HttpsRedirect.httpRoutes(innerRoutes).orNotFound,
      HttpsRedirect.httpApp(innerRoutes.orNotFound),
    ).parTraverse_ { app =>
      val noHeadersReq = Request[IO](method = GET, uri = Uri(path = Uri.Path.Root))
      app(noHeadersReq).map(_.status).assertEquals(Status.Ok) *>
        app(noHeadersReq).flatMap(_.as[String]).assertEquals("pong")
    }
  }
}
