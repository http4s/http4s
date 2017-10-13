package org.http4s

import cats.kernel.laws.OrderLaws
import org.http4s.internal.parboiled2.CharPredicate
import org.http4s.testing.HttpCodecTests

class FragmentSpec extends Http4sSpec {
  "equals" should {
    "be consistent with equality of the values" in prop { (a: Fragment, b: Fragment) =>
      (a == b) must_== (a.value == b.value)
    }
  }

  "hashCode" should {
    "be consistent with equality" in prop { (a: Fragment, b: Fragment) =>
      (a != b) || (a.## == b.##)
    }
  }

  "compare" should {
    "be consistent with value.compare" in prop { (a: Fragment, b: Fragment) =>
      a.compare(b) must_== a.value.compareTo(b.value)
    }
  }

  "parse" should {
    "reject invalid fragments" in prop { s: String =>
      !s.forall(CharPredicate.Alpha ++ CharPredicate.Digit ++ ":/?%") ==>
        (Fragment.parse(s) must beLeft)
    }

    "reject invalid percent sequences" in {
      Fragment.parse("%0") must beLeft
      Fragment.parse("I am your %0G and will be respected as such") must beLeft
    }
  }

  "literal syntax" should {
    "accept valid literals" in {
      fragment"foo" must_== Fragment("foo")
    }

    "reject invalid literals" in {
      illTyped("""fragment"нет"""")
      true
    }
  }

  checkAll("Order[Fragment]", OrderLaws[Fragment].order)
  checkAll("HttpCodec[Fragment]", HttpCodecTests[Fragment].httpCodec)
}
