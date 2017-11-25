package org.http4s

import cats.implicits._
import cats.kernel.laws.discipline.OrderTests
import org.http4s.testing.HttpCodecTests

class QValueSpec extends Http4sSpec {
  import QValue._

  checkAll("Order[QValue]", OrderTests[QValue].order)
  checkAll("HttpCodec[QValue]", HttpCodecTests[QValue].httpCodec)

  "sort by descending q-value" in {
    prop { (x: QValue, y: QValue) =>
      x.thousandths > y.thousandths ==> (x > y)
    }
  }

  "fromDouble should be consistent with fromThousandths" in {
    forall(0 to 1000) { i =>
      fromDouble(i / 1000.0) must_== fromThousandths(i)
    }
  }

  "fromString should be consistent with fromThousandths" in {
    forall(0 to 1000) { i =>
      fromString((i / 1000.0).toString) must_== fromThousandths(i)
    }
  }

  "literal syntax should be consistent with successful fromDouble" in {
    Right(q(1.0)) must_== fromDouble(1.0)
    Right(q(0.5)) must_== fromDouble(0.5)
    Right(q(0.0)) must_== fromDouble(0.0)
    // q(2.0) // doesn't compile: out of range
    // q(0.5 + 0.1) // doesn't compile, not a literal
  }
}
