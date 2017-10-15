package org.http4s

import cats.kernel.laws.OrderLaws
import org.http4s.Uri.Host
import org.http4s.internal.parboiled2.CharPredicate
import org.http4s.testing.HttpCodecTests

class HostSpec extends Http4sSpec {
  "equals" should {
    "be consistent with equalsIgnoreCase of the values" in prop { (a: Host, b: Host) =>
      (a == b) must_== a.value.equalsIgnoreCase(b.value)
    }
  }

  "hashCode" should {
    "be consistent with equality" in prop { (a: Host, b: Host) =>
      (a != b) || (a.## must_== b.##)
    }
  }

  "parse" should {
    "reject invalid registered names" in { s: String =>
      !s.forall(CharPredicate.Alpha ++ CharPredicate.Digit ++ "-._~!$&'()*+,;=[]") ==>
        (Host.parse(s) must beLeft)
    }

    "reject invalid IPv6 addresses" in {
      Host.parse("[nope]") must beLeft
    }
  }

  "literal syntax" should {
    "accept valid literals" in {
      host"localhost" must_== Host.localhost
    }

    "reject invalid literals" in {
      illTyped("""host"нет"""")
      true
    }
  }

  checkAll("Eq[Host]", OrderLaws[Host].eqv)
  checkAll("HttpCodec[Host]", HttpCodecTests[Host].httpCodec)
}
