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

package org.http4s.client.middleware

import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.client._
import org.http4s.dsl.io._
import org.http4s.headers.Cookie
import org.http4s.implicits._

class CookieJarSuite extends Http4sSuite {
  val epoch: HttpDate = HttpDate.Epoch

  test("CookieJar middleware should extract a cookie and apply it correctly") {
    val routes = HttpRoutes
      .of[IO] {
        case GET -> Root / "get-cookie" =>
          val resp = Response[IO](Status.Ok).addCookie(
            ResponseCookie(
              name = "foo",
              content = "bar",
              domain = Some("example.com"),
              expires = HttpDate.MaxValue.some,
            )
          )
          resp.pure[IO]
        case req @ GET -> Root / "test-cookie" =>
          req.headers
            .get[Cookie]
            .fold(
              Response[IO](Status.InternalServerError)
            )(_ => Response[IO](Status.Ok))
            .pure[IO]
      }
      .orNotFound

    val client = Client.fromHttpApp(routes)

    for {
      jar <- CookieJar.jarImpl[IO]
      testClient = CookieJar(jar)(client)
      _ <- testClient.successful(Request[IO](Method.GET, uri"http://example.com/get-cookie"))
      second <- testClient.successful(Request[IO](Method.GET, uri"http://example.com/test-cookie"))
    } yield assert(second)
  }

  test("cookie should apply if the given domain matches") {
    val req = Request[IO](Method.GET, uri = uri"http://example.com")
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = Some("example.com"),
    )
    assert(CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should not apply if not given a domain") {
    val req = Request[IO](Method.GET, uri = uri"http://example.com")
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = None,
    )
    assert(!CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should apply if a subdomain") {
    val req = Request[IO](Method.GET, uri = uri"http://api.example.com")
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = Some("example.com"),
    )
    assert(CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should not apply if the wrong subdomain") {
    val req = Request[IO](Method.GET, uri = uri"http://api.example.com")
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = Some("bad.example.com"),
    )
    assert(!CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should not apply if the superdomain") {
    val req = Request[IO](Method.GET, uri = uri"http://example.com")
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = Some("bad.example.com"),
    )
    assert(!CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should not apply a secure cookie to an http request") {
    val req = Request[IO](Method.GET, uri = uri"http://example.com")
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = Some("example.com"),
      secure = true,
    )
    assert(!CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should apply a secure cookie to an https request") {
    val req = Request[IO](Method.GET, uri = uri"https://example.com")
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = Some("example.com"),
      secure = true,
    )
    assert(CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should not apply to a host that is a prefix collision") {
    val req = Request[IO](Method.GET, uri = uri"http://evilexample.com")
    val cookie = ResponseCookie("foo", "bar", domain = Some("example.com"))
    assert(!CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should not apply when the cookie domain is an internal substring") {
    val req = Request[IO](Method.GET, uri = uri"http://api.example.com.attacker.net")
    val cookie = ResponseCookie("foo", "bar", domain = Some("example.com"))
    assert(!CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should not apply to a request without a host") {
    val req = Request[IO](Method.GET, uri = uri"/some/path")
    val cookie = ResponseCookie("foo", "bar", domain = Some("example.com"))
    assert(!CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should match an IP host only when the domain is identical") {
    val req = Request[IO](Method.GET, uri = uri"http://192.168.0.1")
    assert(
      CookieJar.cookieAppliesToRequest(
        req,
        ResponseCookie("foo", "bar", domain = Some("192.168.0.1")),
      )
    )
    assert(
      !CookieJar.cookieAppliesToRequest(
        req,
        ResponseCookie("foo", "bar", domain = Some("168.0.1")),
      )
    )
  }

  test("cookie should not apply when the path is a non-boundary prefix") {
    val req = Request[IO](Method.GET, uri = uri"http://example.com/public/admin-docs")
    val cookie = ResponseCookie("foo", "bar", domain = Some("example.com"), path = Some("/admin"))
    assert(!CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should not apply to a sibling path sharing a prefix") {
    val req = Request[IO](Method.GET, uri = uri"http://example.com/administrator")
    val cookie = ResponseCookie("foo", "bar", domain = Some("example.com"), path = Some("/admin"))
    assert(!CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should apply to a path at a segment boundary") {
    val req = Request[IO](Method.GET, uri = uri"http://example.com/admin/users")
    val cookie = ResponseCookie("foo", "bar", domain = Some("example.com"), path = Some("/admin"))
    assert(CookieJar.cookieAppliesToRequest(req, cookie))
  }

  test("cookie should apply when the path is an exact match") {
    val req = Request[IO](Method.GET, uri = uri"http://example.com/admin")
    val cookie = ResponseCookie("foo", "bar", domain = Some("example.com"), path = Some("/admin"))
    assert(CookieJar.cookieAppliesToRequest(req, cookie))
  }
}
