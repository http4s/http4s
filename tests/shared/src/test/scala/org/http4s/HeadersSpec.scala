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

import cats.data.NonEmptyList
import cats.kernel.laws.discipline.MonoidTests
import cats.kernel.laws.discipline.OrderTests
import org.http4s.headers._
import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.header._
import org.typelevel.ci._

class HeadersSpec extends Http4sSuite {
  private val clength = `Content-Length`.unsafeFromLong(10)
  private val raw = Header.Raw(ci"raw-header", "Raw value")

  private val base = Headers(clength, raw)

  test("Headers should Not find a header that isn't there") {
    assertEquals(base.get[`Content-Type`], None)
  }

  test("Headers should Find an existing header and return its parsed form") {
    assertEquals(base.get[`Content-Length`], Some(clength))
    assertEquals(base.get(ci"raw-header"), Some(NonEmptyList.of(raw)))
  }

  test("contains") {
    assert(base.contains[`Content-Length`])
    assert(!base.contains[`Content-Type`])
  }

  test("Headers should Replaces headers") {
    val newlen = `Content-Length`.zero
    assertEquals(base.put(newlen).get[`Content-Length`], Some(newlen))
  }

  test("Headers should also find headers created raw") {
    val headers: Headers = Headers(
      Cookie(RequestCookie("foo", "bar")),
      Header.Raw(ci"Cookie", RequestCookie("baz", "quux").toString),
    )
    assertEquals(headers.get[Cookie].map(_.values.length), Some(2))
  }

  test(
    "Headers should Remove duplicate headers which are not of type Recurring on concatenation (++)"
  ) {
    val clength = Header.Raw(ci"Content-Length", "4")
    val hs = Headers(clength) ++ Headers(clength)
    assertEquals(hs.headers.length, 1)
    assertEquals(hs.headers.head, clength)
  }

  test("Headers should Allow multiple Set-Cookie headers") {
    val h1 = `Set-Cookie`(ResponseCookie("foo1", "bar1"))
    val h2 = `Set-Cookie`(ResponseCookie("foo2", "bar2"))
    val hs = Headers(clength) ++ Headers(h1, h2)
    assertEquals(hs.headers.count(_.name == `Set-Cookie`.name), 2)
    assertEquals(hs.headers.contains(clength.toRaw1), true)
  }

  // TODO this isn't really "raw headers" anymore
  test("Headers should Work with Raw headers (++)") {
    val foo = ContentCoding.unsafeFromString("foo")
    val bar = ContentCoding.unsafeFromString("bar")
    val h1 = `Accept-Encoding`(foo)
    val h2 = `Accept-Encoding`(bar)
    val hs = Headers(clength) ++ Headers(h1) ++ Headers(h2)
    assertEquals(hs.get[`Accept-Encoding`], Some(`Accept-Encoding`(bar)))
    assertEquals(hs.get[`Content-Length`], Some(clength))
  }

  test("Headers should Preserve original headers when processing") {
    val rawAuth = Header.Raw(ci"Authorization", "test this")

    // Mapping to strings because Header equality is based on the *parsed* version
    assert((Headers(rawAuth) ++ base).headers.map(_.toString).contains(rawAuth.toString))
  }

  test("Headers should hash the same when constructed with the same contents") {
    val h1 = Headers("Test-Header" -> "Value")
    val h2 = Headers("Test-Header" -> "Value")
    val h3 = Headers("Test-Header" -> "Value", "TestHeader" -> "other value")
    val h4 = Headers("TestHeader" -> "other value", "Test-Header" -> "Value")
    val h5 = Headers("Test-Header" -> "Value", "TestHeader" -> "other value")
    assertEquals(h1.hashCode(), h2.hashCode())
    assert(h1.equals(h2))
    assert(h2.equals(h1))
    assert(!h1.equals(h3))
    assert(!h3.equals(h4))
    assert(h3.equals(h5))
  }

  test("Headers as ToRaw") {
    val headers: Headers = Headers(
      Cookie(RequestCookie("foo", "bar")),
      Header.Raw(ci"Cookie", RequestCookie("baz", "quux").toString),
    )
    assertEquals(Headers.apply(headers), headers)
  }

  test("Headers#mkString") {
    val h1 = Headers("Header-One" -> "value one", "Header-Two" -> "value two")
    val h2 = Headers(
      "Header-One" -> "value one",
      "Header-Two" -> "value two",
      "Header-Three" -> "value three",
    )
    val expectedString1 = "Headers(Header-One: value one, Header-Two: value two)"
    val expectedString2 =
      "Headers(Header-One: value one, Header-Two: value two, Header-Three: <REDACTED>)"
    val expectedString3 = "Header-One: value one, Header-Two: value two"
    val expectedString4 = "Header-One: value one, Header-Two: value two, Header-Three: <REDACTED>"

    assertEquals(
      h1.mkString("Headers(", ", ", ")", Headers.SensitiveHeaders.contains),
      expectedString1,
    )
    assertEquals(h2.mkString("Headers(", ", ", ")", _.toString == "Header-Three"), expectedString2)
    assertEquals(h1.mkString(", ", Headers.SensitiveHeaders.contains), expectedString3)
    assertEquals(h2.mkString(", ", _.toString == "Header-Three"), expectedString4)
  }

  checkAll("Monoid[Headers]", MonoidTests[Headers].monoid)
  checkAll("Order[Headers]", OrderTests[Headers].order)
}
