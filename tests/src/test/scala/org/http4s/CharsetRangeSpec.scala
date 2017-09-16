package org.http4s

import org.http4s.CharsetRange.`*`
import org.scalacheck.Prop.forAll
import org.scalacheck.Arbitrary._

import scala.concurrent.duration._
import scalaz.scalacheck.ScalazProperties
import scalaz.syntax.order._

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

  checkAll(ScalazProperties.equal.laws[CharsetRange])
}
