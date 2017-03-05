package org.http4s

import org.http4s.CharsetRange.`*`
import org.scalacheck.Prop.forAll
import org.scalacheck.Arbitrary._

import scala.concurrent.duration._
import scalaz.scalacheck.ScalazProperties
import scalaz.syntax.order._

class CharsetRangeSpec extends Http4sSpec {
  "*" should {
    "be satisfied by any charset when q > 0" in {
      prop { (range: CharsetRange.`*`, cs: Charset) =>
        range.qValue > QValue.Zero ==> { range isSatisfiedBy cs }
      }
    }

    "not be satisfied when q = 0" in {
      prop { cs: Charset =>
        !(`*`.withQValue(QValue.Zero) isSatisfiedBy cs)
      }
    }
  }

  "atomic charset ranges" should {
    "be satisfied by themselves if q > 0" in {
      forAll (arbitrary[CharsetRange.Atom] suchThat (_.qValue > QValue.Zero)) { range =>
        range isSatisfiedBy range.charset
      }
    }

    "not be satisfied by any other charsets" in {
      prop { (range: CharsetRange.Atom, cs: Charset) =>
        range.charset != cs ==> { !(range isSatisfiedBy cs) }
      }
    }
  }

  checkAll(ScalazProperties.equal.laws[CharsetRange])
}
