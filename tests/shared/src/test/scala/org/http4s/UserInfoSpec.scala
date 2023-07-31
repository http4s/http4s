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

import cats.kernel.laws.discipline.HashTests
import cats.kernel.laws.discipline.OrderTests
import cats.syntax.all._
import org.http4s.Uri.UserInfo
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.laws.discipline.arbitrary._
import org.http4s.util.Renderer.renderString
import org.http4s.util.UrlCodingUtils._
import org.scalacheck.Prop._

class UserInfoSpec extends Http4sSuite {
  checkAll("Order[UserInfo]", OrderTests[UserInfo].order)
  checkAll("Hash[UserInfo]", HashTests[UserInfo].hash)
  checkAll("HttpCodec[UserInfo]", HttpCodecTests[UserInfo].httpCodec)

  test("render should handle basic characters") {
    assertEquals(renderString(UserInfo("abc123", Some("def456"))), "abc123:def456")
  }

  test("render should encode gendelims in username") {
    assertEquals(renderString(UserInfo(":/?#[]@", None)), "%3A%2F%3F%23%5B%5D%40")
  }

  test("render should encode gendelims except ':' in password") {
    assertEquals(renderString(UserInfo("hi", Some(":/?#[]@"))), "hi::%2F%3F%23%5B%5D%40")
  }

  test("render should skip encoding subdelims in username") {
    assertEquals(renderString(UserInfo("!$&'()*+,;=", None)), "!$&'()*+,;=")
  }

  test("render should skip encoding subdelims in password") {
    assertEquals(renderString(UserInfo("hi", Some("!$&'()*+,;="))), "hi:!$&'()*+,;=")
  }

  test("render should use a colon for empty passwords ") {
    assertEquals(renderString(UserInfo("hi", Some(""))), "hi:")
  }

  test("fromString should split on the first colon") {
    assertEquals(UserInfo.fromString("a:b:c"), Right(UserInfo("a", Some("b:c"))))
  }

  test("fromString should not split on encoded colons") {
    assertEquals(UserInfo.fromString("a%3Ab:c"), Right(UserInfo("a:b", Some("c"))))
  }

  test("fromString should parse empty") {
    assertEquals(UserInfo.fromString(""), Right(UserInfo("", None)))
  }

  test("fromString should parse empty password") {
    assertEquals(UserInfo.fromString("abc:"), Right(UserInfo("abc", Some(""))))
  }

  test("fromString should parse without password") {
    assertEquals(UserInfo.fromString("abc"), Right(UserInfo("abc", None)))
  }

  test("fromString should parse empty username") {
    assertEquals(UserInfo.fromString(":123"), Right(UserInfo("", Some("123"))))
  }

  test("fromString should parse username with containing a '+'") {
    assertEquals(UserInfo.fromString("+:abc"), Right(UserInfo("+", Some("abc"))))
  }

  test("fromString should parse password with containing a '+'") {
    assertEquals(UserInfo.fromString("abc:+"), Right(UserInfo("abc", Some("+"))))
  }

  test("fromString should reject userinfos with invalid characters") {
    forAll { (s: String) =>
      !s.forall(Uri.Unreserved ++ GenDelims ++ SubDelims ++ ":") ==>
        (UserInfo.fromString(s).isLeft)
    }
  }

  test("compare should be consistent with (username, password)") {
    forAll { (xs: List[UserInfo]) =>
      def tupled(u: UserInfo) = (u.username, u.password)
      xs.sorted.map(tupled) == xs.map(tupled).sorted
    }
  }

  test("compare should be consistent with Ordered") {
    forAll { (a: UserInfo, b: UserInfo) =>
      math.signum(a.compareTo(b)) == math.signum(a.compare(b))
    }
  }

  test("bug2713 should roundTrip userinfo with plus sign") {
    val userInfo = UserInfo("username+", Some("password+"))
    assertEquals(HttpCodec[UserInfo].parse(renderString(userInfo)), Right(userInfo))
  }

  test("bug2767 should reject userinfos with invalid characters") {
    val s = "@"
    assert(UserInfo.fromString(s).isLeft)
  }

}
