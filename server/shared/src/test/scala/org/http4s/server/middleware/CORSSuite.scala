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
import org.http4s.Http4sSuite
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.syntax.all._
import org.typelevel.ci._

import scala.concurrent.duration._

class CORSSuite extends Http4sSuite {
  private val routes = HttpRoutes.of[IO] {
    case req if req.pathInfo === path"/foo" => Response[IO](Ok).withEntity("foo").pure[IO]
    case req if req.pathInfo === path"/vary" =>
      Response[IO](Ok).putHeaders(Header.Raw(ci"Vary", "X-Old-Vary")).pure[IO]
  }
  private val app = routes.orNotFound

  private val exampleOrigin = Origin.Host(Uri.Scheme.https, Uri.RegName("example.com"), None)
  private val exampleOriginHeader = Origin.HostList(NonEmptyList.of(exampleOrigin))

  private def nonCorsReq = Request[IO](uri = uri"/foo")
  private def nonPreflightReq = nonCorsReq.putHeaders(exampleOriginHeader: Origin)
  private def preflightReq = nonPreflightReq
    .withMethod(Method.OPTIONS)
    .putHeaders(
      `Access-Control-Request-Method`(Method.POST),
      Header.Raw(ci"Access-Control-Request-Headers", "X-Cors-Suite"),
    )

  private def assertAllowOrigin[F[_]](resp: Response[F], origin: Option[String]) =
    assertEquals(
      resp.headers.get(ci"Access-Control-Allow-Origin").map(_.head.value),
      origin.map(_.toString),
    )

  private def assertAllowCredentials[F[_]](resp: Response[F], b: Boolean) =
    assertEquals(resp.headers.get[`Access-Control-Allow-Credentials`].isDefined, b)

  private def assertExposeHeaders[F[_]](resp: Response[F], names: Option[CIString]) =
    assertEquals(
      resp.headers.get[`Access-Control-Expose-Headers`].map(h => CIString(h.value)),
      names,
    )

  private def assertAllowMethods[F[_]](resp: Response[F], methods: Option[String]) =
    assertEquals(
      resp.headers
        .get(`Access-Control-Allow-Methods`.name)
        .map(_.map(_.value).toList.mkString(", ")),
      methods,
    )

  private def assertAllowHeaders[F[_]](resp: Response[F], headers: Option[CIString]) =
    assertEquals(
      resp.headers.get[`Access-Control-Allow-Headers`].map(h => CIString(h.value)),
      headers,
    )

  private def assertMaxAge[F[_]](resp: Response[F], deltaSeconds: Option[Long]) =
    assertEquals(
      resp.headers.get(ci"Access-Control-Max-Age").map(_.head.value),
      deltaSeconds.map(_.toString),
    )

  private def assertVary[F[_]](resp: Response[F], headers: Option[CIString]) =
    assertEquals(
      resp.headers.get(ci"Vary").map(hs => CIString(hs.map(_.value).toList.mkString(", "))),
      headers,
    )

  test("withAllowOriginAll, non-CORS request") {
    CORS.policy.withAllowOriginAll(app).run(nonCorsReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, None)
    }
  }

  test("withAllowOriginAll, non-preflight request") {
    CORS.policy.withAllowOriginAll(app).run(nonPreflightReq).map { resp =>
      assertAllowOrigin(resp, "*".some)
      assertVary(resp, None)
    }
  }

  test("withAllowOriginAll, OPTIONS request without Access-Control-Request-Method") {
    CORS.policy.withAllowOriginAll(app).run(nonCorsReq.withMethod(Method.OPTIONS)).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, ci"Access-Control-Request-Method, Access-Control-Request-Headers".some)
    }
  }

  test("withAllowOriginAll, preflight request") {
    CORS.policy.withAllowOriginAll(app).run(preflightReq).map { resp =>
      assertAllowOrigin(resp, "*".some)
      assertVary(resp, ci"Access-Control-Request-Method, Access-Control-Request-Headers".some)
    }
  }

  test("withAllowOriginHeader, non-CORS request") {
    CORS.policy.withAllowOriginHeader(_ => true)(app).run(nonCorsReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, ci"Origin".some)
    }
  }

  test("withAllowOriginHeader, non-preflight request with matching origin") {
    CORS.policy.withAllowOriginHeader(Set(exampleOriginHeader))(app).run(nonPreflightReq).map {
      resp =>
        assertAllowOrigin(resp, Some("https://example.com"))
        assertVary(resp, ci"Origin".some)
    }
  }

  test("withAllowOriginHeader, OPTIONS request without Access-Control-Request-Method") {
    CORS.policy
      .withAllowOriginHeader(Set(exampleOriginHeader))(app)
      .run(nonCorsReq.withMethod(Method.OPTIONS))
      .map { resp =>
        assertAllowOrigin(resp, None)
        assertVary(
          resp,
          ci"Origin, Access-Control-Request-Method, Access-Control-Request-Headers".some,
        )
      }
  }

  test("withAllowOriginHeader, preflight request with matching origin") {
    CORS.policy.withAllowOriginHeader(Set(exampleOriginHeader))(app).run(preflightReq).map { resp =>
      assertAllowOrigin(resp, Some("https://example.com"))
      assertVary(
        resp,
        ci"Origin, Access-Control-Request-Method, Access-Control-Request-Headers".some,
      )
    }
  }

  test("withAllowOriginHeader, non-preflight request with non-matching origin") {
    CORS.policy.withAllowOriginHeader(_ => false)(app).run(nonPreflightReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, ci"Origin".some)
    }
  }

  test("withAllowOriginHeader, preflight request with non-matching origin") {
    CORS.policy.withAllowOriginHeader(_ => false)(app).run(preflightReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(
        resp,
        ci"Origin, Access-Control-Request-Method, Access-Control-Request-Headers".some,
      )
    }
  }

  test("withAllowOriginHost, non-CORS request") {
    CORS.policy.withAllowOriginHost(_ => true)(app).run(nonCorsReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, ci"Origin".some)
    }
  }

  test("withAllowOriginHost, non-preflight request with matching origin") {
    CORS.policy.withAllowOriginHost(Set(exampleOrigin))(app).run(nonPreflightReq).map { resp =>
      assertAllowOrigin(resp, Some("https://example.com"))
      assertVary(resp, ci"Origin".some)
    }
  }

  test("withAllowOriginHeader, non-preflight request with non-matching origin") {
    CORS.policy.withAllowOriginHeader(_ => false)(app).run(nonPreflightReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, ci"Origin".some)
    }
  }

  test("withAllowOriginHostCi, non-CORS request") {
    CORS.policy.withAllowOriginHostCi(_ => true)(app).run(nonCorsReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, ci"Origin".some)
    }
  }

  test("withAllowOriginHostCi, non-preflight request with matching origin") {
    CORS.policy.withAllowOriginHostCi(Set(ci"HTTPS://EXAMPLE.COM"))(app).run(nonPreflightReq).map {
      resp =>
        assertAllowOrigin(resp, Some("https://example.com"))
        assertVary(resp, ci"Origin".some)
    }
  }

  test("withAllowOriginHostCi, non-preflight request with non-matching origin") {
    CORS.policy.withAllowOriginHostCi(_ => false)(app).run(nonPreflightReq).map { resp =>
      assertAllowOrigin(resp, None)
      assertVary(resp, ci"Origin".some)
    }
  }

  test("withCredentials(true), specific origin, non-preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(Set(exampleOriginHeader))
      .withAllowCredentials(true)
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertAllowCredentials(resp, true)
      }
  }

  test("withCredentials(true), specific origin, preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(Set(exampleOriginHeader))
      .withAllowCredentials(true)
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowCredentials(resp, true)
      }
  }

  test("withCredentials(false), specific origin, non-preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(Set(exampleOriginHeader))
      .withAllowCredentials(false)
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withCredentials(false), specific origin, preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(Set(exampleOriginHeader))
      .withAllowCredentials(false)
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withCredentials(true), any origin, non-preflight request with matching origin") {
    CORS.policy.withAllowOriginAll
      .withAllowCredentials(true)
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withCredentials(true), any origin, preflight request with matching origin") {
    CORS.policy.withAllowOriginAll
      .withAllowCredentials(true)
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withCredentials(false), any origin, non-preflight request with matching origin") {
    CORS.policy.withAllowOriginAll
      .withAllowCredentials(false)
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withCredentials(false), any origin, preflight request with matching origin") {
    CORS.policy.withAllowOriginAll
      .withAllowCredentials(false)
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowCredentials(resp, false)
      }
  }

  test("withExposeHeadersAll, non-preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersAll
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertExposeHeaders(resp, ci"*".some)
      }
  }

  test("withExposeHeadersAll, preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersAll, non-preflight request with non-matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => false)
      .withExposeHeadersAll
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersIn, non-preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersIn(Set(ci"Content-Encoding", ci"X-Cors-Suite"))
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertExposeHeaders(resp, ci"Content-Encoding, X-Cors-Suite".some)
      }
  }

  test("withExposeHeadersIn, preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersIn(Set(ci"Content-Encoding", ci"X-Cors-Suite"))
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersIn, non-preflight request with non-matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => false)
      .withExposeHeadersIn(Set(ci"Content-Encoding", ci"X-Cors-Suite"))
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersNone, non-preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withExposeHeadersNone
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withExposeHeadersNone, non-preflight request with non-matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => false)
      .withExposeHeadersNone
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertExposeHeaders(resp, None)
      }
  }

  test("withAllowMethodsAll, non-preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowMethodsAll
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertAllowMethods(resp, None)
        assertVary(resp, Some(ci"Origin"))
      }
  }

  test(
    "withAllowMethodsAll, credentials allowed, preflight request with matching origin, fails on literal wildcard"
  ) {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowCredentials(true)
      .withAllowMethodsAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowOrigin(resp, None)
        assertAllowMethods(resp, None)
        assertVary(resp, Some(ci"Origin, Access-Control-Request-Headers"))
      }
  }

  test("withAllowMethodsAll, credentials disallowed, preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowCredentials(false)
      .withAllowMethodsAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowMethods(resp, Some("*"))
        assertVary(resp, Some(ci"Origin, Access-Control-Request-Headers"))
      }
  }

  test("withAllowMethodsAll, preflight request with non-matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => false)
      .withAllowMethodsAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowMethods(resp, None)
        assertVary(resp, Some(ci"Origin, Access-Control-Request-Headers"))
      }
  }

  test("withAllowMethodsIn, preflight request with non-matching origin and matching method") {
    CORS.policy
      .withAllowOriginHeader(_ => false)
      .withAllowMethodsIn(Set(Method.GET, Method.POST))
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowMethods(resp, None)
        assertVary(
          resp,
          Some(ci"Origin, Access-Control-Request-Method, Access-Control-Request-Headers"),
        )
      }
  }

  test("withAllowMethodsIn, preflight request with matching origin and method") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowMethodsIn(Set(Method.GET, Method.POST))
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowMethods(resp, Some("GET, POST"))
        assertVary(
          resp,
          Some(ci"Origin, Access-Control-Request-Method, Access-Control-Request-Headers"),
        )
      }
  }

  test("withAllowMethodsIn, preflight request with matching origin and non-matching method") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowMethodsIn(Set(Method.GET, Method.POST))
      .apply(app)
      .run(preflightReq.putHeaders(`Access-Control-Request-Method`(Method.PUT)))
      .map { resp =>
        assertAllowMethods(resp, None)
        assertVary(
          resp,
          Some(ci"Origin, Access-Control-Request-Method, Access-Control-Request-Headers"),
        )
      }
  }

  test("withAllowHeadersAll, non-preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersAll
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertAllowHeaders(resp, None)
        assertVary(resp, Some(ci"Origin"))
      }
  }

  test(
    "withAllowHeadersAll, credentials allowed, preflight request with matching origin, fails on literal wildcard"
  ) {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowCredentials(true)
      .withAllowHeadersAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowOrigin(resp, None)
        assertAllowHeaders(resp, None)
        assertVary(resp, Some(ci"Origin, Access-Control-Request-Method"))
      }
  }

  test("withAllowHeadersAll, credentials disallowed, preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowCredentials(false)
      .withAllowHeadersAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, Some(ci"*"))
        assertVary(resp, Some(ci"Origin, Access-Control-Request-Method"))
      }
  }

  test("withAllowHeadersAll, preflight request with non-matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => false)
      .withAllowHeadersAll
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, None)
        assertVary(resp, Some(ci"Origin, Access-Control-Request-Method"))
      }
  }

  test("withAllowHeadersIn, preflight request with non-matching origin and matching headers") {
    CORS.policy
      .withAllowOriginHeader(_ => false)
      .withAllowHeadersIn(Set(ci"X-Cors-Suite-1", ci"X-Cors-Suite-2"))
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, None)
        assertVary(
          resp,
          Some(ci"Origin, Access-Control-Request-Method, Access-Control-Request-Headers"),
        )
      }
  }

  test("withAllowHeadersIn, preflight request with matching origin and headers") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersIn(Set(ci"X-Cors-Suite", ci"X-Cors-Suite-2"))
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, Some(ci"X-Cors-Suite, X-Cors-Suite-2"))
        assertVary(
          resp,
          Some(ci"Origin, Access-Control-Request-Method, Access-Control-Request-Headers"),
        )
      }
  }

  test("withAllowHeadersIn, preflight request with matching origin and some non-matching headers") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersIn(Set(ci"X-Cors-Suite-1", ci"X-Cors-Suite-2"))
      .apply(app)
      .run(
        preflightReq.putHeaders(
          Header.Raw(ci"Access-Control-Request-Headers", "X-Cors-Suite-1, X-Cors-Suite-3")
        )
      )
      .map { resp =>
        assertAllowHeaders(resp, None)
        assertVary(
          resp,
          Some(ci"Origin, Access-Control-Request-Method, Access-Control-Request-Headers"),
        )
      }
  }

  test("withAllowHeadersIn, preflight request with matching origin and all matching headers") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersIn(Set(ci"X-Cors-Suite-1", ci"X-Cors-Suite-2"))
      .apply(app)
      .run(
        preflightReq.putHeaders(Header.Raw(ci"Access-Control-Request-Headers", "X-Cors-Suite-1"))
      )
      .map { resp =>
        assertAllowHeaders(resp, ci"X-Cors-Suite-1, X-Cors-Suite-2".some)
        assertVary(
          resp,
          Some(ci"Origin, Access-Control-Request-Method, Access-Control-Request-Headers"),
        )
      }
  }

  test("withAllowHeadersReflect, preflight request with non-matching origin and matching headers") {
    CORS.policy
      .withAllowOriginHeader(_ => false)
      .withAllowHeadersReflect
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, None)
        assertVary(
          resp,
          Some(ci"Origin, Access-Control-Request-Method, Access-Control-Request-Headers"),
        )
      }
  }

  test("withAllowHeadersReflect, preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersReflect
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, Some(ci"X-Cors-Suite"))
        assertVary(
          resp,
          Some(ci"Origin, Access-Control-Request-Method, Access-Control-Request-Headers"),
        )
      }
  }

  test("withAllowHeadersStatic, preflight request with non-matching origin and matching headers") {
    CORS.policy
      .withAllowOriginHeader(_ => false)
      .withAllowHeadersStatic(Set(ci"X-Cors-Suite-1", ci"X-Cors-Suite-2"))
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, None)
        assertVary(resp, Some(ci"Origin, Access-Control-Request-Method"))
      }
  }

  test("withAllowHeadersStatic, preflight request with matching origin and headers") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersStatic(Set(ci"X-Cors-Suite", ci"X-Cors-Suite-2"))
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertAllowHeaders(resp, Some(ci"X-Cors-Suite, X-Cors-Suite-2"))
        assertVary(resp, Some(ci"Origin, Access-Control-Request-Method"))
      }
  }

  test(
    "withAllowHeadersStatic, preflight request with matching origin and some non-matching headers"
  ) {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersStatic(Set(ci"X-Cors-Suite-1", ci"X-Cors-Suite-2"))
      .apply(app)
      .run(
        preflightReq.putHeaders(
          Header.Raw(ci"Access-Control-Request-Headers", "X-Cors-Suite-1, X-Cors-Suite-3")
        )
      )
      .map { resp =>
        assertAllowHeaders(resp, Some(ci"X-Cors-Suite-1, X-Cors-Suite-2"))
        assertVary(resp, Some(ci"Origin, Access-Control-Request-Method"))
      }
  }

  test("withAllowHeadersStatic, preflight request with matching origin and all matching headers") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withAllowHeadersStatic(Set(ci"X-Cors-Suite-1", ci"X-Cors-Suite-2"))
      .apply(app)
      .run(
        preflightReq.putHeaders(Header.Raw(ci"Access-Control-Request-Headers", "X-Cors-Suite-1"))
      )
      .map { resp =>
        assertAllowHeaders(resp, Some(ci"X-Cors-Suite-1, X-Cors-Suite-2"))
        assertVary(resp, Some(ci"Origin, Access-Control-Request-Method"))
      }
  }

  test("withMaxAge, non-preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withMaxAge(10.seconds)
      .apply(app)
      .run(nonPreflightReq)
      .map { resp =>
        assertMaxAge(resp, None)
      }
  }

  test("withMaxAge, preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withMaxAge(10.seconds)
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertMaxAge(resp, 10L.some)
      }
  }

  test("withMaxAge, preflight request with non-matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => false)
      .withMaxAge(10.seconds)
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertMaxAge(resp, None)
      }
  }

  test("withMaxAge negative, preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withMaxAge(-10.seconds)
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertMaxAge(resp, 0L.some)
      }
  }

  test("withMaxAgeDefault, preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withMaxAgeDefault
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertMaxAge(resp, None)
      }
  }

  test("withMaxAgeDisableCaching, preflight request with matching origin") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .withMaxAgeDisableCaching
      .apply(app)
      .run(preflightReq)
      .map { resp =>
        assertMaxAge(resp, -1L.some)
      }
  }

  test("merges old Vary header") {
    CORS.policy
      .withAllowOriginHeader(_ => true)
      .apply(app)
      .run(nonPreflightReq.withUri(uri"/vary"))
      .map { resp =>
        assertVary(resp, ci"X-Old-Vary, Origin".some)
      }
  }

  test("returns 200 on preflight requests even if routes don't handle OPTIONS") {
    val routes = HttpRoutes.empty[IO]
    CORS.policy.apply(routes).orNotFound.run(preflightReq).map(_.status).assertEquals(Status.Ok)
  }
}

@deprecated("This suite tests a deprecated feature", "0.21.27")
class CORSDeprecatedSuite extends Http4sSuite {
  private val routes = HttpRoutes.of[IO] {
    case req if req.pathInfo === path"/foo" => Response[IO](Ok).withEntity("foo").pure[IO]
    case req if req.pathInfo === path"/bar" => Response[IO](Unauthorized).withEntity("bar").pure[IO]
  }

  private val cors1 = CORS(routes)
  private val cors2 = CORS(
    routes,
    CORSConfig.default
      .withAnyOrigin(false)
      .withAllowCredentials(false)
      .withMaxAge(0.seconds)
      .withAllowedOrigins(Set("http://allowed.com"))
      .withAllowedHeaders(Some(Set("User-Agent", "Keep-Alive", "Content-Type")))
      .withExposedHeaders(Some(Set("x-header"))),
  )

  private def headerCheck(h: Header.Raw): Boolean = h.name == ci"Access-Control-Max-Age"

  final def matchHeader(hs: Headers, name: CIString, expected: String): Boolean =
    hs.get(name).fold(false)(_.exists(_.value === expected))

  private def buildRequest(path: String, method: Method = GET) =
    Request[IO](uri = Uri(path = Uri.Path.unsafeFromString(path)), method = method)
      .withHeaders("Origin" -> "http://allowed.com", "Access-Control-Request-Method" -> "GET")

  test("Be omitted when unrequested") {
    val req = buildRequest("/other")
    cors1.orNotFound(req).map(_.headers.headers.exists(headerCheck _)).assertEquals(false) *>
      cors2.orNotFound(req).map(_.headers.headers.exists(headerCheck _)).assertEquals(false)
  }

  test("Respect Access-Control-Allow-Credentials") {
    val req = buildRequest("/foo")
    CORS(
      routes,
      CORSConfig.default
        .withAnyOrigin(false)
        .withAllowCredentials(true)
        .withAllowedOrigins(Function.const(true)),
    )
      .orNotFound(req)
      .map(_.headers.get(ci"Access-Control-Allow-Credentials").map(_.head.value))
      .assertEquals("true".some) *>
      cors2
        .orNotFound(req)
        .map(_.headers.get(ci"Access-Control-Allow-Credentials"))
        .assertEquals(None)
  }

  test("Respect Access-Control-Allow-Headers in preflight call") {
    val req = buildRequest("/foo", OPTIONS)
    cors2
      .orNotFound(req)
      .map { resp =>
        matchHeader(
          resp.headers,
          ci"Access-Control-Allow-Headers",
          "User-Agent, Keep-Alive, Content-Type",
        )
      }
      .assert
  }

  test("Respect Access-Control-Expose-Headers in non-preflight call") {
    val req = buildRequest("/foo")
    cors2
      .orNotFound(req)
      .map { resp =>
        matchHeader(resp.headers, ci"Access-Control-Expose-Headers", "x-header")
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
          ci"Access-Control-Allow-Origin",
          "http://allowed.com",
        )
      )
      .assert *>
      cors2
        .orNotFound(req)
        .map(resp =>
          resp.status.isSuccess && matchHeader(
            resp.headers,
            ci"Access-Control-Allow-Origin",
            "http://allowed.com",
          )
        )
        .assert
  }

  test("Always respond with 200 and empty body for OPTIONS request") {
    val req = buildRequest("/bar", OPTIONS)
    cors1.orNotFound(req).map(_.headers.headers.exists(headerCheck _)).assert *>
      cors2.orNotFound(req).map(_.headers.headers.exists(headerCheck _)).assert
  }

  test("Respond with 403 when origin is not valid") {
    val req = buildRequest("/bar").withHeaders("Origin" -> "http://blah.com/")
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
        .putHeaders("Vary" -> "Origin,Accept")
        .withEntity("foo")
        .pure[IO]
    })

    service
      .orNotFound(req)
      .map { resp =>
        matchHeader(resp.headers, ci"Vary", "Origin,Accept")
      }
      .assert
  }

  test("Be created via httpRoutes constructor") {
    CORS.httpRoutes(routes)
    // The default does nothing now, so we can only test that it compiles
  }

  test("Be created via httpApp constructor") {
    CORS.httpApp(routes.orNotFound)
    // The default does nothing now, so we can only test that it compiles
  }

  test("Suppress Access-Control-Allow-Credentials on non-preflight request when anyOrigin is set") {
    val cors =
      CORS(routes.orNotFound, CORSConfig.default.withAnyOrigin(true).withAllowCredentials(true))
    val req = buildRequest("/foo")
    cors.run(req).map { resp =>
      assertEquals(
        resp.headers.get("Access-Control-Allow-Origin".ci).map(_.head.value).map(CIString(_)),
        ci"http://allowed.com".some,
      )
      assertEquals(resp.headers.get("Access-Control-Allow-Credentials".ci).map(_.head.value), None)
    }
  }

  test("Suppress Access-Control-Allow-Credentials on preflight request when anyOrigin is set") {
    val cors =
      CORS(routes.orNotFound, CORSConfig.default.withAnyOrigin(true).withAllowCredentials(true))
    val req = buildRequest("/foo")
    cors.run(req).map { resp =>
      assertEquals(
        resp.headers.get("Access-Control-Allow-Origin".ci).map(_.head.value).map(CIString(_)),
        ci"http://allowed.com".some,
      )
      assertEquals(resp.headers.get("Access-Control-Allow-Credentials".ci).map(_.head.value), None)
    }
  }
}
