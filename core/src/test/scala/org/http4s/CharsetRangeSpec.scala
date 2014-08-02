package org.http4s

import org.http4s.CharsetRange.`*`
import org.http4s.scalacheck.ScalazProperties
import org.scalacheck.{Gen, Prop}
import org.scalacheck.Prop.forAll
import org.scalacheck.Arbitrary._
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import scalaz.syntax.order._

class CharsetRangeSpec extends Http4sSpec {
  "*" should {
    "be satisfied by any charset when q > 0" in {
      prop { (range: CharsetRange.`*`, cs: Charset) =>
        range.qValue > QValue.Zero ==> { range isSatisfiedBy cs must beTrue }
      }
    }

    "not be satisfied when q = 0" in {
      prop { cs: Charset =>
        `*`.withQValue(QValue.Zero) isSatisfiedBy cs must beFalse
      }
    }
  }

  "atomic charset ranges" should {
    "be satisfied by themselves if q > 0" in {
      forAll (arbitrary[CharsetRange.Atom] suchThat (_.qValue > QValue.Zero)) { range =>
        range isSatisfiedBy range.charset must beTrue
      }
    }

    "not be satisfied by any other charsets" in {
      prop { (range: CharsetRange.Atom, cs: Charset) =>
        range.charset != cs ==> { range isSatisfiedBy cs must beFalse }
      }
    }
  }

  "ordering" should {
    "be lawful" in {
      check(ScalazProperties.order.laws[CharsetRange])
    }

    "prefer higher q values" in {
      prop { (x: CharsetRange, y: CharsetRange) =>
        x.qValue > y.qValue ==> x < y
      }
    }

    "equally prefer equal q values" in {
      prop { (x: CharsetRange, y: CharsetRange, q: QValue) =>
        x.withQValue(q) === y.withQValue(q)
      }
    }
  }
}
