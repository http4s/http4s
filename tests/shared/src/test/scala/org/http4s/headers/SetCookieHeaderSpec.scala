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
package headers

import cats.syntax.all._

class SetCookieHeaderSpec extends Http4sSuite {
  private def parse(value: String): `Set-Cookie` = `Set-Cookie`.parse(value).valueOr(throw _)

  test("Set-Cookie parser should parse a set cookie") {
    val cookiestr =
      "myname=\"foo\"; Domain=example.com; Max-Age=1; Path=value; SameSite=Strict; Secure; HttpOnly"
    val c = parse(cookiestr).cookie
    assertEquals(c.name, "myname")
    assertEquals(c.domain, Some("example.com"))
    assertEquals(c.content, """"foo"""")
    assertEquals(c.maxAge, Some(1L))
    assertEquals(c.path, Some("value"))
    assertEquals(c.sameSite, Some(SameSite.Strict))
    assertEquals(c.secure, true)
    assertEquals(c.httpOnly, true)
  }

  test("Set-Cookie parser should default to None") {
    val cookiestr = "myname=\"foo\"; Domain=value; Max-Age=1; Path=value"
    val c = parse(cookiestr).cookie
    assertEquals(c.sameSite, None)
  }

  test("Set-Cookie parser should parse a set cookie with lowercase attributes") {
    val cookiestr =
      "myname=\"foo\"; domain=example.com; max-age=1; path=value; samesite=strict; secure; httponly"
    val c = parse(cookiestr).cookie
    assertEquals(c.name, "myname")
    assertEquals(c.domain, Some("example.com"))
    assertEquals(c.content, """"foo"""")
    assertEquals(c.maxAge, Some(1L))
    assertEquals(c.path, Some("value"))
    assertEquals(c.sameSite, Some(SameSite.Strict))
    assertEquals(c.secure, true)
    assertEquals(c.httpOnly, true)
  }

  test("Set-Cookie parser should parse a set cookie without spaces") {
    val cookiestr =
      "myname=\"foo\";Domain=example.com;Max-Age=1;Path=value;SameSite=Strict;Secure;HttpOnly"
    val c = parse(cookiestr).cookie
    assertEquals(c.name, "myname")
    assertEquals(c.domain, Some("example.com"))
    assertEquals(c.content, """"foo"""")
    assertEquals(c.maxAge, Some(1L))
    assertEquals(c.path, Some("value"))
    assertEquals(c.sameSite, Some(SameSite.Strict))
    assertEquals(c.secure, true)
    assertEquals(c.httpOnly, true)
  }

  test("Set-Cookie parser should parse with a domain with a leading dot") {
    val cookiestr = "myname=\"foo\"; Domain=.example.com"
    val c = parse(cookiestr).cookie
    assertEquals(c.domain, Some(".example.com"))
  }

  test("Set-Cookie parser should parse with an extension") {
    val cookiestr = "myname=\"foo\"; http4s=fun"
    val c = parse(cookiestr).cookie
    assertEquals(c.extension, Some("http4s=fun"))
  }

  test("Set-Cookie parser should parse with two extensions") {
    val cookiestr = "myname=\"foo\"; http4s=fun; rfc6265=not-fun"
    val c = parse(cookiestr).cookie
    assertEquals(c.extension, Some("http4s=fun; rfc6265=not-fun"))
  }

  test("Set-Cookie parser should parse with two extensions around a common attribute") {
    val cookiestr = "myname=\"foo\"; http4s=fun; Domain=example.com; rfc6265=not-fun"
    val c = parse(cookiestr).cookie
    assertEquals(c.domain, Some("example.com"))
    assertEquals(c.extension, Some("http4s=fun; rfc6265=not-fun"))
  }

}
