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

import cats.syntax.all._
import cats.kernel.laws.discipline.{HashTests, OrderTests}
import org.http4s.Uri.UserInfo
import org.http4s.util.UrlCodingUtils._
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.util.Renderer.renderString

class UserInfoSpec extends Http4sSpec {
  checkAll("Order[UserInfo]", OrderTests[UserInfo].order)
  checkAll("Hash[UserInfo]", HashTests[UserInfo].hash)
  checkAll("HttpCodec[UserInfo]", HttpCodecTests[UserInfo].httpCodec)

  "render" should {
    "handle basic characters" in {
      renderString(UserInfo("abc123", Some("def456"))) must_== "abc123:def456"
    }

    "encode gendelims in username" in {
      renderString(UserInfo(":/?#[]@", None)) must_== "%3A%2F%3F%23%5B%5D%40"
    }

    "encode gendelims except ':' in password" in {
      renderString(UserInfo("hi", Some(":/?#[]@"))) must_== "hi::%2F%3F%23%5B%5D%40"
    }

    "skip encoding subdelims in username" in {
      renderString(UserInfo("!$&'()*+,;=", None)) must_== "!$&'()*+,;="
    }

    "skip encoding subdelims in password" in {
      renderString(UserInfo("hi", Some("!$&'()*+,;="))) must_== "hi:!$&'()*+,;="
    }

    "use a colon for empty passwords " in {
      renderString(UserInfo("hi", Some(""))) must_== "hi:"
    }
  }

  "fromString" should {
    "split on the first colon" in {
      UserInfo.fromString("a:b:c") must_== Right(UserInfo("a", Some("b:c")))
    }

    "not split on encoded colons" in {
      UserInfo.fromString("a%3Ab:c") must_== Right(UserInfo("a:b", Some("c")))
    }

    "parse empty" in {
      UserInfo.fromString("") must_== Right(UserInfo("", None))
    }

    "parse empty password" in {
      UserInfo.fromString("abc:") must_== Right(UserInfo("abc", Some("")))
    }

    "parse without password" in {
      UserInfo.fromString("abc") must_== Right(UserInfo("abc", None))
    }

    "parse empty username" in {
      UserInfo.fromString(":123") must_== Right(UserInfo("", Some("123")))
    }

    "parse username with containing a '+'" in {
      UserInfo.fromString("+:abc") must_== Right(UserInfo("+", Some("abc")))
    }

    "parse password with containing a '+'" in {
      UserInfo.fromString("abc:+") must_== Right(UserInfo("abc", Some("+")))
    }

    "reject userinfos with invalid characters" in prop { (s: String) =>
      !s.forall(Uri.Unreserved ++ GenDelims ++ SubDelims ++ ":") ==>
        (UserInfo.fromString(s) must beLeft)
    }
  }

  "compare" should {
    "be consistent with (username, password)" in prop { (xs: List[UserInfo]) =>
      def tupled(u: UserInfo) = (u.username, u.password)
      xs.sorted.map(tupled) must_== xs.map(tupled).sorted
    }

    "be consistent with Ordered" in prop { (a: UserInfo, b: UserInfo) =>
      math.signum(a.compareTo(b)) must_== math.signum(a.compare(b))
    }
  }

  "bug2713" should {
    "roundTrip userinfo with plus sign" in {
      val userInfo = UserInfo("username+", Some("password+"))
      HttpCodec[UserInfo].parse(renderString(userInfo)) must_== Right(userInfo)
    }
  }

  "bug2767" should {
    "reject userinfos with invalid characters" in {
      val s = "@"
      UserInfo.fromString(s) must beLeft
    }
  }
}
