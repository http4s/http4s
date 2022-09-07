/*
 * Copyright 2013 http4s.org
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

import cats.effect.IO
import org.http4s.Charset._
import org.http4s.headers._
import org.typelevel.ci._

class ResponderSpec extends Http4sSuite {
  private val resp = Response[IO](Status.Ok)

  test("Responder should Change status") {
    val resp = Response[IO](Status.Ok)

    assertEquals(resp.status, Status.Ok)

    assertEquals(resp.withStatus(Status.BadGateway).status, Status.BadGateway)
  }

  test("Responder should Replace content type") {
    assert(resp.contentType.isEmpty)
    val c1 = resp
      .putHeaders(`Content-Length`.unsafeFromLong(4))
      .withContentType(`Content-Type`(MediaType.text.plain))
      .putHeaders(Host("foo"))

    assertEquals(c1.headers.headers.count(_.name == ci"Content-Type"), 1)
    assertEquals(c1.headers.headers.count(_.name == ci"Content-Length"), 1)
    assertEquals(c1.headers.headers.length, 3)
    assertEquals(c1.contentType, Some(`Content-Type`(MediaType.text.plain)))

    val c2 = c1.withContentType(`Content-Type`(MediaType.application.json, `UTF-8`))

    assertEquals(c2.contentType, Some(`Content-Type`(MediaType.application.json, `UTF-8`)))
    assertEquals(c2.headers.headers.count(_.name == ci"Content-Type"), 1)
    assertEquals(c2.headers.headers.count(_.name == ci"Content-Length"), 1)
    assertEquals(c2.headers.headers.count(_.name == ci"Host"), 1)
  }

  test("Responder should Remove headers") {
    val wHeader = resp.putHeaders(Connection.close)
    val maybeHeaderT = wHeader.headers.get[Connection]
    assertEquals(maybeHeaderT, Some(Connection.close))

    val newHeaders = wHeader.removeHeader[Connection]
    assert(!newHeaders.headers.contains[Connection])
  }

  test("Responder should Replace all headers") {
    val wHeader =
      resp.putHeaders(Connection.close, `Content-Length`.unsafeFromLong(10), Host("foo"))
    assertEquals(wHeader.headers.headers.length, 3)

    val newHeaders = wHeader.withHeaders(Date(HttpDate.Epoch))
    assertEquals(newHeaders.headers.headers.length, 1)
    assert(!newHeaders.headers.contains[Connection])
  }

  test("Responder should Replace all headers II") {
    val wHeader =
      resp.putHeaders(Connection.close, `Content-Length`.unsafeFromLong(10), Host("foo"))
    assertEquals(wHeader.headers.headers.length, 3)

    val newHeaders = wHeader.withHeaders(Headers(Date(HttpDate.Epoch)))
    assertEquals(newHeaders.headers.headers.length, 1)
    assert(!newHeaders.headers.contains[Connection])
  }

  test("Responder should Filter headers") {
    val wHeader =
      resp.putHeaders(Connection.close, `Content-Length`.unsafeFromLong(10), Host("foo"))
    assertEquals(wHeader.headers.headers.length, 3)

    val newHeaders = wHeader.filterHeaders(_.name != ci"Connection")
    assertEquals(newHeaders.headers.headers.length, 2)
    assert(!newHeaders.headers.contains[Connection])
  }

  test("Responder should Set cookie from tuple") {
    assertEquals(resp.addCookie("foo", "bar").cookies, List(ResponseCookie("foo", "bar")))
  }

  test("Responder should Set cookie from Cookie") {
    assertEquals(
      resp.addCookie(ResponseCookie("foo", "bar")).cookies,
      List(ResponseCookie("foo", "bar")),
    )
  }

  test("Responder should Set multiple cookies") {
    assertEquals(
      resp
        .addCookie(ResponseCookie("foo", "bar"))
        .addCookie(ResponseCookie("baz", "quux"))
        .cookies,
      List(
        ResponseCookie("foo", "bar"),
        ResponseCookie("baz", "quux"),
      ),
    )
  }

  test("Responder should Remove cookie") {
    val cookie = ResponseCookie("foo", "bar")
    assertEquals(
      resp.removeCookie(cookie).cookies,
      List(
        ResponseCookie("foo", "", expires = Option(HttpDate.Epoch))
      ),
    )
  }

  test("Responder should Remove multiple cookies") {
    val cookie1 = ResponseCookie("foo1", "bar")
    val cookie2 = ResponseCookie("foo2", "baz")
    assertEquals(
      resp.removeCookie(cookie1).removeCookie(cookie2).cookies,
      List(
        ResponseCookie("foo1", "", expires = Option(HttpDate.Epoch)),
        ResponseCookie("foo2", "", expires = Option(HttpDate.Epoch)),
      ),
    )
  }
}
