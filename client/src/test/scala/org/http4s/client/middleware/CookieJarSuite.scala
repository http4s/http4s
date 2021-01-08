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

import cats.syntax.all._
import cats.effect._
import org.http4s._
import org.http4s.implicits._
import org.http4s.client._
import org.http4s.ResponseCookie
import org.http4s.dsl.io._
import org.http4s.headers.Cookie

class CookieJarSuite extends Http4sSuite {
  val epoch: HttpDate = HttpDate.Epoch

  test("extract a cookie and apply it correctly") {
    val routes = HttpRoutes
      .of[IO] {
        case GET -> Root / "get-cookie" =>
          val resp = Response[IO](Status.Ok).addCookie(
            ResponseCookie(
              name = "foo",
              content = "bar",
              domain = Some("google.com"),
              expires = HttpDate.MaxValue.some
            ))
          resp.pure[IO]
        case req @ GET -> Root / "test-cookie" =>
          req.headers
            .get(Cookie)
            .fold(
              Response[IO](Status.InternalServerError)
            )(_ => Response[IO](Status.Ok))
            .pure[IO]
      }
      .orNotFound

    val client = Client.fromHttpApp(routes)

    val result = for {
      jar <- CookieJar.jarImpl[IO]
      testClient = CookieJar(jar)(client)
      _ <- testClient.successful(Request[IO](Method.GET, uri"http://google.com/get-cookie"))
      second <- testClient.successful(Request[IO](Method.GET, uri"http://google.com/test-cookie"))
    } yield second

    result.assertEquals(true)
  }

  test("apply if the given domain matches") {
    val req = Request[IO](Method.GET, uri = uri"http://google.com")
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = Some("google.com")
    )
    assertEquals(CookieJar.cookieAppliesToRequest(req, cookie), true)
  }

  test("not apply if not given a domain") {
    val req = Request[IO](Method.GET, uri = Uri.uri("http://google.com"))
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = None
    )
    assertEquals(CookieJar.cookieAppliesToRequest(req, cookie), false)
  }

  test("apply if a subdomain") {
    val req = Request[IO](Method.GET, uri = Uri.uri("http://api.google.com"))
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = Some("google.com")
    )
    assertEquals(CookieJar.cookieAppliesToRequest(req, cookie), true)
  }

  test("not apply if the wrong subdomain") {
    val req = Request[IO](Method.GET, uri = Uri.uri("http://api.google.com"))
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = Some("bad.google.com")
    )
    assertEquals(CookieJar.cookieAppliesToRequest(req, cookie), false)
  }

  test("not apply if the superdomain") {
    val req = Request[IO](Method.GET, uri = Uri.uri("http://google.com"))
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = Some("bad.google.com")
    )
    assertEquals(CookieJar.cookieAppliesToRequest(req, cookie), false)
  }

  test("not apply a secure cookie to an http request") {
    val req = Request[IO](Method.GET, uri = uri"http://google.com")
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = Some("google.com"),
      secure = true
    )
    assertEquals(CookieJar.cookieAppliesToRequest(req, cookie), false)
  }

  test("apply a secure cookie to an https request") {
    val req = Request[IO](Method.GET, uri = uri"https://google.com")
    val cookie = ResponseCookie(
      "foo",
      "bar",
      domain = Some("google.com"),
      secure = true
    )
    assertEquals(CookieJar.cookieAppliesToRequest(req, cookie), true)
  }
}
