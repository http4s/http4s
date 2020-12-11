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
package parser

import org.http4s.headers.`Set-Cookie`
import org.specs2.mutable.Specification

class SetCookieHeaderSpec extends Specification with HeaderParserHelper[`Set-Cookie`] {
  def hparse(value: String): ParseResult[`Set-Cookie`] = HttpHeaderParser.SET_COOKIE(value)

  "Set-Cookie parser" should {
    "parse a set cookie" in {
      val cookiestr =
        "myname=\"foo\"; Domain=example.com; Max-Age=1; Path=value; SameSite=Strict; Secure;HttpOnly"
      val c = parse(cookiestr).cookie
      c.name must be_==("myname")
      c.domain must beSome("example.com")
      c.content must be_==("foo")
      c.maxAge must be_==(Some(1))
      c.path must beSome("value")
      c.sameSite must be_==(SameSite.Strict)
      c.secure must be_==(true)
      c.httpOnly must be_==(true)
    }

    "default to SameSite=Lax" in {
      val cookiestr = "myname=\"foo\"; Domain=value; Max-Age=1; Path=value"
      val c = parse(cookiestr).cookie
      c.sameSite must be_==(SameSite.Lax)
    }

    "parse a set cookie with lowercase attributes" in {
      val cookiestr =
        "myname=\"foo\"; domain=example.com; max-age=1; path=value; samesite=strict; secure; httponly"
      val c = parse(cookiestr).cookie
      c.name must be_==("myname")
      c.domain must beSome("example.com")
      c.content must be_==("foo")
      c.maxAge must be_==(Some(1))
      c.path must be_==(Some("value"))
      c.sameSite must be_==(SameSite.Strict)
      c.secure must be_==(true)
      c.httpOnly must be_==(true)
    }

    "parse with a domain with a leading dot" in {
      val cookiestr = "myname=\"foo\"; Domain=.example.com"
      val c = parse(cookiestr).cookie
      c.domain must beSome(".example.com")
    }

    "parse with a domain with a leading dot" in {
      val cookiestr = "myname=\"foo\"; Domain=.example.com"
      val c = parse(cookiestr).cookie
      c.domain must beSome(".example.com")
    }

    "parse with an extension" in {
      val cookiestr = "myname=\"foo\"; http4s=fun"
      val c = parse(cookiestr).cookie
      c.extension must beSome("http4s=fun")
    }

    "parse with two extensions" in {
      val cookiestr = "myname=\"foo\"; http4s=fun; rfc6265=not-fun"
      val c = parse(cookiestr).cookie
      c.extension must beSome("http4s=fun; rfc6265=not-fun")
    }

    "parse with an two extensions around a common attribute" in {
      val cookiestr = "myname=\"foo\"; http4s=fun; Domain=example.com; rfc6265=not-fun"
      val c = parse(cookiestr).cookie
      c.domain must beSome("example.com")
      c.extension must beSome("http4s=fun; rfc6265=not-fun")
    }
  }
}

class CookieHeaderSpec extends Specification with HeaderParserHelper[headers.Cookie] {
  def hparse(value: String): ParseResult[headers.Cookie] = HttpHeaderParser.COOKIE(value)

  val cookiestr = "key1=value1; key2=\"value2\""
  val cookiestrSemicolon: String = cookiestr + ";"
  val cookies = Seq(RequestCookie("key1", "value1"), RequestCookie("key2", "value2"))

  "Cookie parser" should {
    "parse a cookie" in {
      parse(cookiestr).values.toList must be_==(cookies)
    }
    "parse a cookie (semicolon at the end)" in {
      parse(cookiestrSemicolon).values.toList must be_==(cookies)
    }
  }
}
