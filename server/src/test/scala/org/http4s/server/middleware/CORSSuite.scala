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

import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.http4s.headers._
import org.http4s.Http4sSuite
import org.http4s.util.{CaseInsensitiveString => CIString}

class CORSSuite extends Http4sSuite {
  val routes = HttpRoutes.of[IO] {
    case req if req.pathInfo == "/foo" => Response[IO](Ok).withEntity("foo").pure[IO]
    case req if req.pathInfo == "/bar" => Response[IO](Unauthorized).withEntity("bar").pure[IO]
  }
  val app = routes.orNotFound

  val exampleOrigin = Origin.Host(Uri.Scheme.https, Uri.RegName("example.com"), None)
  val exampleOriginHeader = Origin.HostList(NonEmptyList.of(exampleOrigin))

  def nonCorsReq = Request[IO](uri = uri"/foo")
  def corsReq = nonCorsReq.putHeaders(exampleOriginHeader)

  def assertAllowOrigin[F[_]](resp: Response[F], origin: Option[String]) =
    assertEquals(
      resp.headers.get(`Access-Control-Allow-Origin`).map(_.value),
      origin.map(_.toString))

  def assertAllowCredentials[F[_]](resp: Response[F], b: Boolean) =
    assertEquals(
      resp.headers.get(`Access-Control-Allow-Credentials`).map(_.value),
      if (b) Some("true") else None)

  def assertExposeHeaders[F[_]](resp: Response[F], names: Option[CIString]) =
    assertEquals(resp.headers.get(`Access-Control-Expose-Headers`).map(_.value.ci), names)

  test("withAllowAnyOrigin, non-CORS request") {
    CORS.withAllowAnyOrigin(app).run(nonCorsReq).map { resp =>
      assertAllowOrigin(resp, None)
    }
  }

  test("withAllowAnyOrigin, CORS request") {
    CORS.withAllowAnyOrigin(app).run(corsReq).map { resp =>
      assertAllowOrigin(resp, "*".some)
    }
  }

  test("withAllowOriginHeader, non-CORS request") {
    CORS.withAllowOriginHeader(_ => true)(app).run(nonCorsReq).map { resp =>
      assertAllowOrigin(resp, None)
    }
  }

  test("withAllowOriginHeader, CORS request with matching origin") {
    CORS.withAllowOriginHeader(Set(exampleOriginHeader))(app).run(corsReq).map { resp =>
      assertAllowOrigin(resp, Some("https://example.com"))
    }
  }

  test("withAllowOriginHeader, CORS request with non-matching origin") {
    CORS.withAllowOriginHeader(_ => false)(app).run(corsReq).map { resp =>
      assertAllowOrigin(resp, None)
    }
  }

  test("withAllowOriginHost, non-CORS request") {
    CORS.withAllowOriginHost(_ => true)(app).run(nonCorsReq).map { resp =>
      assertAllowOrigin(resp, None)
    }
  }

  test("withAllowOriginHost, CORS request with matching origin") {
    CORS.withAllowOriginHost(Set(exampleOrigin))(app).run(corsReq).map { resp =>
      assertAllowOrigin(resp, Some("https://example.com"))
    }
  }

  test("withAllowOriginHeader, CORS request with non-matching origin") {
    CORS.withAllowOriginHeader(_ => false)(app).run(corsReq).map { resp =>
      assertAllowOrigin(resp, None)
    }
  }

  test("withAllowOriginHostCi, non-CORS request") {
    CORS.withAllowOriginHostCi(_ => true)(app).run(nonCorsReq).map { resp =>
      assertAllowOrigin(resp, None)
    }
  }

  test("withAllowOriginHostCi, CORS request with matching origin") {
    CORS.withAllowOriginHostCi(Set("HTTPS://EXAMPLE.COM".ci))(app).run(corsReq).map { resp =>
      assertAllowOrigin(resp, Some("https://example.com"))
    }
  }

  test("withAllowOriginHostCi, CORS request with non-matching origin") {
    CORS.withAllowOriginHostCi(_ => false)(app).run(corsReq).map { resp =>
      assertAllowOrigin(resp, None)
    }
  }

  test("withCredentials(true), specific origin, CORS request with matching origin") {
    CORS
      .withAllowOriginHeader(Set(exampleOriginHeader))
      .withAllowCredentials(true)
      .apply(app)
      .run(corsReq)
      .map { resp =>
        assertAllowCredentials(resp, true)
      }
  }

  test("withCredentials(false), specific origin, CORS request with matching origin") {
    CORS
      .withAllowOriginHeader(Set(exampleOriginHeader))
      .withAllowCredentials(false)
      .apply(app)
      .run(corsReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withCredentials(true), any origin, CORS request with matching origin") {
    CORS.withAllowAnyOrigin
      .withAllowCredentials(true)
      .apply(app)
      .run(corsReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withCredentials(false), any origin, CORS request with matching origin") {
    CORS.withAllowAnyOrigin
      .withAllowCredentials(false)
      .apply(app)
      .run(corsReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withExposeHeadersAll, CORS request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersAll
      .apply(app)
      .run(corsReq)
      .map { resp =>
        assertExposeHeaders(resp, "*".ci.some)
      }
  }

  test("withExposeHeadersAll, CORS request with non-matching origin") {
    CORS
      .withAllowOriginHeader(_ => false)
      .withExposeHeadersAll
      .apply(app)
      .run(corsReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersIn, CORS request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersIn(Set("Content-Encoding".ci, "X-Cors-Suite".ci))
      .apply(app)
      .run(corsReq)
      .map { resp =>
        assertExposeHeaders(resp, "Content-Encoding, X-Cors-Suite".ci.some)
      }
  }

  test("withExposeHeadersIn, CORS request with non-matching origin") {
    CORS
      .withAllowOriginHeader(_ => false)
      .withExposeHeadersIn(Set("Content-Encoding".ci, "X-Cors-Suite".ci))
      .apply(app)
      .run(corsReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersNone, CORS request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersNone
      .apply(app)
      .run(corsReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersNone, CORS request with non-matching origin") {
    CORS
      .withAllowOriginHeader(_ => false)
      .withExposeHeadersNone
      .apply(app)
      .run(corsReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  val cors1 = CORS(routes)
  val cors2 = CORS(
    routes,
    CORSConfig(
      anyOrigin = false,
      allowCredentials = false,
      maxAge = 0,
      allowedOrigins = Set("http://allowed.com"),
      allowedHeaders = Some(Set("User-Agent", "Keep-Alive", "Content-Type")),
      exposedHeaders = Some(Set("x-header"))
    )
  )

  def headerCheck(h: Header): Boolean = h.is(`Access-Control-Max-Age`)
  def matchHeader(hs: Headers, hk: HeaderKey.Extractable, expected: String): Boolean =
    hs.get(hk).fold(false)(_.value === expected)

  def buildRequest(path: String, method: Method = GET) =
    Request[IO](uri = Uri(path = path), method = method).withHeaders(
      Header("Origin", "http://allowed.com"),
      Header("Access-Control-Request-Method", "GET"))

  test("Be omitted when unrequested") {
    val req = buildRequest("foo")
    cors1.orNotFound(req).map(_.headers.toList.exists(headerCheck _)).assertEquals(false) *>
      cors2.orNotFound(req).map(_.headers.toList.exists(headerCheck _)).assertEquals(false)
  }

  test("Respect Access-Control-Allow-Credentials") {
    val req = buildRequest("/foo")
    cors1
      .orNotFound(req)
      .map(resp => matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "true"))
      .assert *>
      cors2
        .orNotFound(req)
        .map(resp => matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "false"))
        .assert
  }

  test("Respect Access-Control-Allow-Headers in preflight call") {
    val req = buildRequest("/foo", OPTIONS)
    cors2
      .orNotFound(req)
      .map { resp =>
        matchHeader(
          resp.headers,
          `Access-Control-Allow-Headers`,
          "User-Agent, Keep-Alive, Content-Type")
      }
      .assert
  }

  test("Respect Access-Control-Expose-Headers in non-preflight call") {
    val req = buildRequest("/foo")
    cors2
      .orNotFound(req)
      .map { resp =>
        matchHeader(resp.headers, `Access-Control-Expose-Headers`, "x-header")
      }
      .assert
  }

  test("Offer a successful reply to OPTIONS on fallthrough") {
    val req = buildRequest("/unexistant", OPTIONS)
    cors1
      .orNotFound(req)
      .map(resp =>
        resp.status.isSuccess && matchHeader(
          resp.headers,
          `Access-Control-Allow-Credentials`,
          "true"))
      .assert *>
      cors2
        .orNotFound(req)
        .map(resp =>
          resp.status.isSuccess && matchHeader(
            resp.headers,
            `Access-Control-Allow-Credentials`,
            "false"))
        .assert
  }

  test("Always respond with 200 and empty body for OPTIONS request") {
    val req = buildRequest("/bar", OPTIONS)
    cors1.orNotFound(req).map(_.headers.toList.exists(headerCheck _)).assert *>
      cors2.orNotFound(req).map(_.headers.toList.exists(headerCheck _)).assert
  }

  test("Respond with 403 when origin is not valid") {
    val req = buildRequest("/bar").withHeaders(Header("Origin", "http://blah.com/"))
    cors2
      .orNotFound(req)
      .map(resp => resp.status.code == 403)
      .assert
  }

  test("Fall through") {
    val req = buildRequest("/2")
    val routes1 = CORS(HttpRoutes.of[IO] { case GET -> Root / "1" => Ok() })
    val routes2 = CORS(HttpRoutes.of[IO] { case GET -> Root / "2" => Ok() })
    (routes1 <+> routes2).orNotFound(req).map(_.status).assertEquals(Ok)
  }

  test("Not replace vary header if already set") {
    val req = buildRequest("/")
    val service = CORS(HttpRoutes.of[IO] { case GET -> Root =>
      Response[IO](Ok)
        .putHeaders(Header("Vary", "Origin,Accept"))
        .withEntity("foo")
        .pure[IO]
    })

    service
      .orNotFound(req)
      .map { resp =>
        matchHeader(resp.headers, `Vary`, "Origin,Accept")
      }
      .assert
  }

  test("Be created via httpRoutes constructor") {
    val cors = CORS.httpRoutes(routes)
    val req = buildRequest("/foo")

    cors
      .orNotFound(req)
      .map(resp => matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "true"))
      .assert
  }

  test("Be created via httpApp constructor") {
    val cors = CORS.httpApp(routes.orNotFound)
    val req = buildRequest("/foo")

    cors
      .run(req)
      .map(resp => matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "true"))
      .assert
  }
}
