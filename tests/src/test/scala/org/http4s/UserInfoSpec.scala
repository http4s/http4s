package org.http4s

import cats.Show
import cats.kernel.laws.OrderLaws
import org.http4s.Uri.UserInfo
import org.http4s.internal.parboiled2.CharPredicate
import org.http4s.testing.HttpCodecTests
import org.http4s.util.UrlCodingUtils.urlEncode

class UserInfoSpec extends Http4sSpec {
  "compare" should {
    "be consistent with username compare" in prop { (a: String, b: String, pw: Option[String]) =>
      UserInfo(a, pw).compare(UserInfo(b, pw)) must_== a.compare(b)
    }

    "be consistent with password compare when usernames equal" in prop {
      (a: String, b: String, u: String) =>
        UserInfo(u, Some(a)).compare(UserInfo(u, Some(b))) must_== a.compare(b)
    }

    "sort no password before any password when usernames equal" in prop { (u: String, p: String) =>
      (UserInfo(u, None).compare(UserInfo(u, Some(p))) must be).lessThan(0)
    }
  }

  "parse" should {
    "split on the first colon" in prop { (u: String, pw: String) =>
      UserInfo.parse(s"${urlEncode(u)}:${urlEncode(pw)}") must beRight(UserInfo(u, Some(pw)))
    }

    "reject invalid user info" in prop { s: String =>
      !s.forall(CharPredicate.Alpha ++ CharPredicate.Digit ++ "-._~!$&'()*+,;=") ==>
        (UserInfo.parse(s) must beLeft)
    }

    "reject invalid percent sequences" in {
      UserInfo.parse("%0") must beLeft
      UserInfo.parse("I am your %0G and will be respected as such") must beLeft
    }
  }

  "literal syntax" should {
    "accept valid literals" in {
      userInfo"insecure:sad" must_== UserInfo("insecure", Some("sad"))
    }

    "reject invalid literals" in {
      illTyped("""userInfo"нет"""")
      true
    }
  }

  "show" should {
    "not render the password" in prop { (u: String, pw: String) =>
      Show[UserInfo].show(UserInfo(u, Some(pw))) should_== s"UserInfo($u,<REDACTED>)"
    }
  }

  checkAll("Order[UserInfo]", OrderLaws[UserInfo].order)
  checkAll("HttpCodec[UserInfo]", HttpCodecTests[UserInfo].httpCodec)
}
