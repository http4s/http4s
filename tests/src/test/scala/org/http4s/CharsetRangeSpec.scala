package org.http4s

import scala.concurrent.duration._

import cats.kernel.laws._
import org.http4s.CharsetRange.`*`
import org.scalacheck._
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
      forAll (arbitrary[CharsetRange.Atom]) { range =>
        range.matches(range.charset)
      }
    }

    "not be satisfied by any other charsets" in {
      prop { (range: CharsetRange.Atom, cs: Charset) =>
        range.charset != cs ==> { !range.matches(cs) }
      }
    }
  }

  checkAll("CharsetRange", OrderLaws[CharsetRange].eqv)
}
