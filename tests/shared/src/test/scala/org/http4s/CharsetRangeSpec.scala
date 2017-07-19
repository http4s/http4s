package org.http4s

import cats.kernel.laws.discipline.EqTests
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Prop.forAll

class CharsetRangeSpec extends Http4sSpec {
  "*" should {
    "match all charsets" in {
      prop { (range: CharsetRange.`*`, cs: Charset) =>
        range.matches(cs)
      }
    }
  }

  "atomic charset ranges" should {
    "match their own charsest" in {
      forAll(arbitrary[CharsetRange.Atom]) { range =>
        range.matches(range.charset)
      }
    }

    "not be satisfied by any other charsets" in {
      prop { (range: CharsetRange.Atom, cs: Charset) =>
        range.charset != cs ==> { !range.matches(cs) }
      }
    }
  }

  checkAll("CharsetRange", EqTests[CharsetRange].eqv)
}
