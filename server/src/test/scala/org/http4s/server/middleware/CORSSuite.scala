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
  def nonPreflightReq = nonCorsReq.putHeaders(exampleOriginHeader)
  def preflightReq = nonPreflightReq
    .withMethod(Method.OPTIONS)
    .putHeaders(
      Header.Raw(`Access-Control-Request-Method`.name, Method.POST.renderString),
      Header.Raw(`Access-Control-Request-Headers`.name, s"X-Cors-Suite")
    )

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

  def assertAllowMethods[F[_]](resp: Response[F], methods: Option[String]) =
    assertEquals(resp.headers.get(`Access-Control-Allow-Methods`).map(_.value), methods)

  def assertAllowHeaders[F[_]](resp: Response[F], headers: Option[CIString]) =
    assertEquals(resp.headers.get(`Access-Control-Allow-Headers`).map(_.value.ci), headers)

  def assertVary[F[_]](resp: Response[F], headers: Option[CIString]) =
    assertEquals(resp.headers.get(Vary).map(_.value.ci), headers)

  test("withAllowAnyOrigin, non-CORS request") {
    CORS.withAllowAnyOrigin(app).run(nonCorsReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, None)
    }
  }

  test("withAllowAnyOrigin, non-preflight request") {
    CORS.withAllowAnyOrigin(app).run(nonPreflightReq).map { resp =>
      assertAllowOrigin(resp, "*".some)
      assertVary(resp, None)
    }
  }

  test("withAllowAnyOrigin, OPTIONS request without Access-Control-Request-Method") {
    CORS.withAllowAnyOrigin(app).run(nonCorsReq.withMethod(Method.OPTIONS)).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, "Access-Control-Request-Method, Access-Control-Request-Headers".ci.some)
    }
  }

  test("withAllowAnyOrigin, preflight request") {
    CORS.withAllowAnyOrigin(app).run(preflightReq).map { resp =>
      assertAllowOrigin(resp, "*".some)
      assertVary(resp, "Access-Control-Request-Method, Access-Control-Request-Headers".ci.some)
    }
  }

  test("withAllowOriginHeader, non-CORS request") {
    CORS.withAllowOriginHeader(_ => true)(app).run(nonCorsReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, "Origin".ci.some)
    }
  }

  test("withAllowOriginHeader, non-preflight request with matching origin") {
    CORS.withAllowOriginHeader(Set(exampleOriginHeader))(app).run(nonPreflightReq).map { resp =>
      assertAllowOrigin(resp, Some("https://example.com"))
      assertVary(resp, "Origin".ci.some)
    }
  }

  test("withAllowOriginHeader, OPTIONS request without Access-Control-Request-Method") {
    CORS
      .withAllowOriginHeader(Set(exampleOriginHeader))(app)
      .run(nonCorsReq.withMethod(Method.OPTIONS))
      .map { resp =>
        assertAllowOrigin(resp, None)
        assertVary(
          resp,
          "Origin, Access-Control-Request-Method, Access-Control-Request-Headers".ci.some)
      }
  }

  test("withAllowOriginHeader, preflight request with matching origin") {
    CORS.withAllowOriginHeader(Set(exampleOriginHeader))(app).run(preflightReq).map { resp =>
      assertAllowOrigin(resp, Some("https://example.com"))
      assertVary(
        resp,
        "Origin, Access-Control-Request-Method, Access-Control-Request-Headers".ci.some)
    }
  }

  test("withAllowOriginHeader, non-preflight request with non-matching origin") {
    CORS.withAllowOriginHeader(_ => false)(app).run(nonPreflightReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, "Origin".ci.some)
    }
  }

  test("withAllowOriginHeader, preflight request with non-matching origin") {
    CORS.withAllowOriginHeader(_ => false)(app).run(preflightReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(
        resp,
        "Origin, Access-Control-Request-Method, Access-Control-Request-Headers".ci.some)
    }
  }

  test("withAllowOriginHost, non-CORS request") {
    CORS.withAllowOriginHost(_ => true)(app).run(nonCorsReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, "Origin".ci.some)
    }
  }

  test("withAllowOriginHost, non-preflight request with matching origin") {
    CORS.withAllowOriginHost(Set(exampleOrigin))(app).run(nonPreflightReq).map { resp =>
      assertAllowOrigin(resp, Some("https://example.com"))
      assertVary(resp, "Origin".ci.some)
    }
  }

  test("withAllowOriginHeader, non-preflight request with non-matching origin") {
    CORS.withAllowOriginHeader(_ => false)(app).run(nonPreflightReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, "Origin".ci.some)
    }
  }

  test("withAllowOriginHostCi, non-CORS request") {
    CORS.withAllowOriginHostCi(_ => true)(app).run(nonCorsReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, "Origin".ci.some)
    }
  }

  test("withAllowOriginHostCi, non-preflight request with matching origin") {
    CORS.withAllowOriginHostCi(Set("HTTPS://EXAMPLE.COM".ci))(app).run(nonPreflightReq).map {
      resp =>
        assertAllowOrigin(resp, Some("https://example.com"))
        assertVary(resp, "Origin".ci.some)
    }
  }

  test("withAllowOriginHostCi, non-preflight request with non-matching origin") {
    CORS.withAllowOriginHostCi(_ => false)(app).run(nonPreflightReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, "Origin".ci.some)
    }
  }

  test("withCredentials(true), specific origin, non-preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(Set(exampleOriginHeader))
      .withAllowCredentials(true)
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertAllowCredentials(resp, true)
      }
  }

  test("withCredentials(true), specific origin, preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(Set(exampleOriginHeader))
      .withAllowCredentials(true)
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowCredentials(resp, true)
      }
  }

  test("withCredentials(false), specific origin, non-preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(Set(exampleOriginHeader))
      .withAllowCredentials(false)
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withCredentials(false), specific origin, preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(Set(exampleOriginHeader))
      .withAllowCredentials(false)
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withCredentials(true), any origin, non-preflight request with matching origin") {
    CORS.withAllowAnyOrigin
      .withAllowCredentials(true)
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withCredentials(true), any origin, preflight request with matching origin") {
    CORS.withAllowAnyOrigin
      .withAllowCredentials(true)
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withCredentials(false), any origin, non-preflight request with matching origin") {
    CORS.withAllowAnyOrigin
      .withAllowCredentials(false)
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withCredentials(false), any origin, preflight request with matching origin") {
    CORS.withAllowAnyOrigin
      .withAllowCredentials(false)
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withExposeHeadersAll, non-preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersAll
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertExposeHeaders(resp, "*".ci.some)
      }
  }

  test("withExposeHeadersAll, preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersAll, non-preflight request with non-matching origin") {
    CORS
      .withAllowOriginHeader(_ => false)
      .withExposeHeadersAll
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersIn, non-preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersIn(Set("Content-Encoding".ci, "X-Cors-Suite".ci))
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertExposeHeaders(resp, "Content-Encoding, X-Cors-Suite".ci.some)
      }
  }

  test("withExposeHeadersIn, preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersIn(Set("Content-Encoding".ci, "X-Cors-Suite".ci))
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersIn, non-preflight request with non-matching origin") {
    CORS
      .withAllowOriginHeader(_ => false)
      .withExposeHeadersIn(Set("Content-Encoding".ci, "X-Cors-Suite".ci))
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersNone, non-preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersNone
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersNone, non-preflight request with non-matching origin") {
    CORS
      .withAllowOriginHeader(_ => false)
      .withExposeHeadersNone
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withAllowMethodsAll, non-preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withAllowMethodsAll
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertAllowMethods(resp, None)
        assertVary(resp, Some("Origin".ci))
      }
  }

  test(
    "withAllowMethodsAll, credentials allowed, preflight request with matching origin, fails on literal wildcard") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withAllowCredentials(true)
      .withAllowMethodsAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowOrigin(resp, None)
        assertAllowMethods(resp, None)
        assertVary(resp, Some("Origin, Access-Control-Request-Headers".ci))
      }
  }

  test("withAllowMethodsAll, credentials disallowed, preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withAllowCredentials(false)
      .withAllowMethodsAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowMethods(resp, Some("*"))
        assertVary(resp, Some("Origin, Access-Control-Request-Headers".ci))
      }
  }

  test("withAllowMethodsAll, preflight request with non-matching origin") {
    CORS
      .withAllowOriginHeader(_ => false)
      .withAllowMethodsAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowMethods(resp, None)
        assertVary(resp, Some("Origin, Access-Control-Request-Headers".ci))
      }
  }

  test("withAllowMethodsIn, preflight request with non-matching origin and matching method") {
    CORS
      .withAllowOriginHeader(_ => false)
      .withAllowMethodsIn(Set(Method.GET, Method.POST))
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowMethods(resp, None)
        assertVary(
          resp,
          Some("Origin, Access-Control-Request-Method, Access-Control-Request-Headers".ci))
      }
  }

  test("withAllowMethodsIn, preflight request with matching origin and method") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withAllowMethodsIn(Set(Method.GET, Method.POST))
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowMethods(resp, Some("GET, POST"))
        assertVary(
          resp,
          Some("Origin, Access-Control-Request-Method, Access-Control-Request-Headers".ci))
      }
  }

  test("withAllowMethodsIn, preflight request with matching origin and non-matching method") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withAllowMethodsIn(Set(Method.GET, Method.POST))
      .apply(app)
      .run(preflightReq.putHeaders(
        Header.Raw(`Access-Control-Request-Method`.name, Method.PUT.renderString)))
      .map { resp =>
        assertAllowMethods(resp, None)
        assertVary(
          resp,
          Some("Origin, Access-Control-Request-Method, Access-Control-Request-Headers".ci))
      }
  }

  test("withAllowHeadersAll, non-preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersAll
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertAllowHeaders(resp, None)
        assertVary(resp, Some("Origin".ci))
      }
  }

  test(
    "withAllowHeadersAll, credentials allowed, preflight request with matching origin, fails on literal wildcard") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withAllowCredentials(true)
      .withAllowHeadersAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowOrigin(resp, None)
        assertAllowHeaders(resp, None)
        assertVary(resp, Some("Origin, Access-Control-Request-Method".ci))
      }
  }

  test("withAllowHeadersAll, credentials disallowed, preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withAllowCredentials(false)
      .withAllowHeadersAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, Some("*".ci))
        assertVary(resp, Some("Origin, Access-Control-Request-Method".ci))
      }
  }

  test("withAllowHeadersAll, preflight request with non-matching origin") {
    CORS
      .withAllowOriginHeader(_ => false)
      .withAllowHeadersAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, None)
        assertVary(resp, Some("Origin, Access-Control-Request-Method".ci))
      }
  }

  test("withAllowHeadersIn, preflight request with non-matching origin and matching headers") {
    CORS
      .withAllowOriginHeader(_ => false)
      .withAllowHeadersIn(Set("X-Cors-Suite-1".ci, "X-Cors-Suite-2".ci))
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, None)
        assertVary(
          resp,
          Some("Origin, Access-Control-Request-Method, Access-Control-Request-Headers".ci))
      }
  }

  test("withAllowHeadersIn, preflight request with matching origin and headers") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersIn(Set("X-Cors-Suite".ci, "X-Cors-Suite-2".ci))
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, Some("X-Cors-Suite, X-Cors-Suite-2".ci))
        assertVary(
          resp,
          Some("Origin, Access-Control-Request-Method, Access-Control-Request-Headers".ci))
      }
  }

  test("withAllowHeadersIn, preflight request with matching origin and some non-matching headers") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersIn(Set("X-Cors-Suite-1".ci, "X-Cors-Suite-2".ci))
      .apply(app)
      .run(preflightReq.putHeaders(
        Header.Raw(`Access-Control-Request-Headers`.name, "X-Cors-Suite-1, X-Cors-Suite-3")))
      .map { resp =>
        assertAllowHeaders(resp, None)
        assertVary(
          resp,
          Some("Origin, Access-Control-Request-Method, Access-Control-Request-Headers".ci))
      }
  }

  test("withAllowHeadersIn, preflight request with matching origin and all matching headers") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersIn(Set("X-Cors-Suite-1".ci, "X-Cors-Suite-2".ci))
      .apply(app)
      .run(preflightReq.putHeaders(
        Header.Raw(`Access-Control-Request-Headers`.name, "X-Cors-Suite-1")))
      .map { resp =>
        assertAllowHeaders(resp, "X-Cors-Suite-1, X-Cors-Suite-2".ci.some)
        assertVary(
          resp,
          Some("Origin, Access-Control-Request-Method, Access-Control-Request-Headers".ci))
      }
  }

  test("withAllowHeadersReflect, preflight request with non-matching origin and matching headers") {
    CORS
      .withAllowOriginHeader(_ => false)
      .withAllowHeadersReflect
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, None)
        assertVary(
          resp,
          Some("Origin, Access-Control-Request-Method, Access-Control-Request-Headers".ci))
      }
  }

  test("withAllowHeadersReflect, preflight request with matching origin") {
    CORS
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersReflect
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, Some("X-Cors-Suite".ci))
        assertVary(
          resp,
          Some("Origin, Access-Control-Request-Method, Access-Control-Request-Headers".ci))
      }
  }
}

@deprecated("This suite tests a deprecated feature", "0.21.27")
trait CORSDeprecatedSuite extends Http4sSuite {
  val routes = HttpRoutes.of[IO] {
    case req if req.pathInfo == "/foo" => Response[IO](Ok).withEntity("foo").pure[IO]
    case req if req.pathInfo == "/bar" => Response[IO](Unauthorized).withEntity("bar").pure[IO]
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
