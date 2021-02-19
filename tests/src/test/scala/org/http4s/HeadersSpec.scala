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

import cats.kernel.laws.discipline.{MonoidTests, OrderTests}
import org.http4s.headers._
import org.http4s.laws.discipline.ArbitraryInstances._
import org.typelevel.ci.CIString

class HeadersSpec extends Http4sSuite {
  val clength = `Content-Length`.unsafeFromLong(10)
  val raw = Header.Raw(CIString("raw-header"), "Raw value")

  val base = Headers.of(clength.toRaw, raw)

  test("Headers should Not find a header that isn't there") {
    assertEquals(base.get(`Content-Base`), None)
  }

  test("Headers should Find an existing header and return its parsed form") {
    assert(base.get(`Content-Length`) == Some(clength))
    assertEquals(base.get(CIString("raw-header")), Some(raw))
  }

  test("Headers should Replaces headers") {
    val newlen = `Content-Length`.zero
    assert(base.put(newlen).get(newlen.key) == Some(newlen))
    assert(base.put(newlen.toRaw).get(newlen.key) == Some(newlen))
  }

  test("Headers should also find headers created raw") {
    val headers = Headers.of(
      org.http4s.headers.`Cookie`(RequestCookie("foo", "bar")),
      Header("Cookie", RequestCookie("baz", "quux").toString)
    )
    assertEquals(headers.get(org.http4s.headers.Cookie).map(_.values.length), Some(2))
  }

  test(
    "Headers should Remove duplicate headers which are not of type Recurring on concatenation (++)") {
    val hs = Headers.of(clength) ++ Headers.of(clength)
    assertEquals(hs.toList.length, 1)
    assertEquals(hs.toList.head, clength)
  }

  test("Headers should Allow multiple Set-Cookie headers") {
    val h1 = `Set-Cookie`(ResponseCookie("foo1", "bar1")).toRaw
    val h2 = `Set-Cookie`(ResponseCookie("foo2", "bar2")).toRaw
    val hs = Headers.of(clength) ++ Headers.of(h1) ++ Headers.of(h2)
    assertEquals(
      hs.toList.count(_.parsed match { case `Set-Cookie`(_) => true; case _ => false }),
      2)
    assertEquals(hs.exists(_ == clength), true)
  }

  test("Headers should Work with Raw headers (++)") {
    val foo = ContentCoding.unsafeFromString("foo")
    val bar = ContentCoding.unsafeFromString("bar")
    val h1 = `Accept-Encoding`(foo).toRaw
    val h2 = `Accept-Encoding`(bar).toRaw
    val hs = Headers.of(clength.toRaw) ++ Headers.of(h1) ++ Headers.of(h2)
    assert(hs.get(`Accept-Encoding`) == Some(`Accept-Encoding`(foo, bar)))
    assertEquals(hs.exists(_ == clength), true)
  }

  // test("Headers should Avoid making copies if there are duplicate collections") {
  //   assertEquals(base ++ Headers.empty eq base, true)
  //   assertEquals(Headers.empty ++ base eq base, true)
  // }

  test("Headers should Preserve original headers when processing") {
    val rawAuth = Header("Authorization", "test this")

    // Mapping to strings because Header equality is based on the *parsed* version
    assert((Headers.of(rawAuth) ++ base).toList.map(_.toString).contains(rawAuth.toString))
  }

  test("Headers should hash the same when constructed with the same contents") {
    val h1 = Headers.of(Header("Test-Header", "Value"))
    val h2 = Headers.of(Header("Test-Header", "Value"))
    val h3 = Headers(List(Header("Test-Header", "Value"), Header("TestHeader", "other value")))
    val h4 = Headers(List(Header("TestHeader", "other value"), Header("Test-Header", "Value")))
    val h5 = Headers(List(Header("Test-Header", "Value"), Header("TestHeader", "other value")))
    assertEquals(h1.hashCode(), h2.hashCode())
    assert(h1.equals(h2))
    assert(h2.equals(h1))
    assert(!h1.equals(h3))
    assert(!h3.equals(h4))
    assert(h3.equals(h5))
  }

  checkAll("Monoid[Headers]", MonoidTests[Headers].monoid)
  checkAll("Order[Headers]", OrderTests[Headers].order)
}
