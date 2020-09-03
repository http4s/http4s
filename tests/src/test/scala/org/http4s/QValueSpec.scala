/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
import cats.kernel.laws.discipline.OrderTests
import org.http4s.laws.discipline.HttpCodecTests

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

    Right(qValue"1.0") must_== fromDouble(1.0)
    Right(qValue"0.5") must_== fromDouble(0.5)
    Right(qValue"0.0") must_== fromDouble(0.0)

    Right(q(0.5 + 0.1)) must_== fromDouble(0.6)
  }

  "literal syntax should reject invalid values" in {
    import org.specs2.execute._, Typecheck._
    import org.specs2.matcher.TypecheckMatchers._

    typecheck {
      """
        q(2.0) // doesn't compile: out of range
      """
    } should not succeed

    typecheck {
      """
        val d: Double = 0.5 + 0.1
        q(d) // doesn't compile, not a literal
      """
    } should not succeed

    typecheck {
      """
        qValue"2.0" // doesn't compile: out of range
      """
    } should not succeed

    typecheck {
      """
        qValue"invalid" // doesn't compile, not parsable as a double
      """
    } should not succeed
  }
}
