package org.http4s

import cats.kernel.laws.OrderLaws
import org.http4s.Uri.Authority
import org.http4s.internal.parboiled2.CharPredicate
import org.http4s.testing.HttpCodecTests

class AuthoritySpec extends Http4sSpec {
  "parse" should {
    "reject invalid authorities" in prop { s: String =>
      !s.forall(CharPredicate.Alpha ++ CharPredicate.Digit ++ "-._~!$&'()*+,;=@:") ==>
        (Authority.parse(s) must beLeft)
    }

    "reject invalid percent sequences" in {
      Authority.parse("%0") must beLeft
      Authority.parse("I am your %0G and will be respected as such") must beLeft
    }
  }

  "literal syntax" should {
    "accept valid literals" in {
      authority"insecure:sad@example.com:80" must_== Authority(
        Some(userInfo"insecure:sad"),
        host"example.com",
        Some(port"80"))
    }

    "reject invalid literals" in {
      illTyped("""authority"нет"""")
      true
    }
  }

  checkAll("Eq[Authority]", OrderLaws[Authority].eqv)
  checkAll("HttpCodec[Authority]", HttpCodecTests[Authority].httpCodec)
}
