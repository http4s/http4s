package org.http4s

import org.http4s.scalacheck.ScalazProperties

class QValueSpec extends Http4sSpec {
  "ordering" should {
    "be lawful" in {
      check(ScalazProperties.order.laws[QValue])
    }

    "prefer higher q values" in {
      prop { (x: QValue, y: QValue) =>
        x.thousandths > y.thousandths ==> (x > y)
      }
    }
  }

  "QValues" should {
    "fromDouble should be consistent with fromThousandths" in {
      forall (0 to 1000) { i =>
        QValue.fromDouble(i / 1000.0) must_== QValue.fromThousandths(i)
      }
    }

    "fromString should be consistent with fromThousandths" in {
      forall (0 to 1000) { i =>
        QValue.fromString((i / 1000.0).toString) must_== QValue.fromThousandths(i)
      }
    }
  }

  "qValueLiterals" should {
    "match toString" in {
      q(1.0) must_== QValue.One
      q(0.5) must_== QValue.fromString("0.5").fold(throw _, identity)
      q(0.0) must_== QValue.Zero
      // q(2.0) // doesn't compile: out of range
      // q(0.5 + 0.1) // doesn't compile, not a literal
    }
  }
}
