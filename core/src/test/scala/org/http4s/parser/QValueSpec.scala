package org.http4s.parser

import org.http4s.scalacheck.ScalazProperties
import org.http4s.{QValue, Http4sSpec}

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
        QValue.fromDouble(i / 1000.0) must_== QValue.fromThousandths(i)
      }
    }
  }
}
