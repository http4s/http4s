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
import org.http4s.headers.`Set-Cookie`
import org.specs2.mutable.Specification

class SetCookieHeaderSpec extends Specification {
  def parse(value: String): `Set-Cookie` = `Set-Cookie`.parse(value).valueOr(throw _)

  "Set-Cookie parser" should {
    "parse a set cookie" in {
      val cookiestr =
        "myname=\"foo\"; Domain=example.com; Max-Age=1; Path=value; SameSite=Strict; Secure; HttpOnly"
      val c = parse(cookiestr).cookie
      c.name must be_==("myname")
      c.domain must beSome("example.com")
      c.content must be_==(""""foo"""")
      c.maxAge must be_==(Some(1))
      c.path must beSome("value")
      c.sameSite must be_==(Some(SameSite.Strict))
      c.secure must be_==(true)
      c.httpOnly must be_==(true)
    }

    "default to None" in {
      val cookiestr = "myname=\"foo\"; Domain=value; Max-Age=1; Path=value"
      val c = parse(cookiestr).cookie
      c.sameSite must be_==(None)
    }

    "parse a set cookie with lowercase attributes" in {
      val cookiestr =
        "myname=\"foo\"; domain=example.com; max-age=1; path=value; samesite=strict; secure; httponly"
      val c = parse(cookiestr).cookie
      c.name must be_==("myname")
      c.domain must beSome("example.com")
      c.content must be_==(""""foo"""")
      c.maxAge must be_==(Some(1))
      c.path must be_==(Some("value"))
      c.sameSite must be_==(Some(SameSite.Strict))
      c.secure must be_==(true)
      c.httpOnly must be_==(true)
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
