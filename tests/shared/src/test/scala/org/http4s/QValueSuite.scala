/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.kernel.laws.discipline.BoundedEnumerableTests
import cats.kernel.laws.discipline.HashTests
import cats.kernel.laws.discipline.OrderTests
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.all._
import org.scalacheck.Prop.forAll
import org.scalacheck.Prop.propBoolean

class QValueSuite extends Http4sSuite {
  import QValue._

  checkAll("Order[QValue]", OrderTests[QValue].order)
  checkAll("HttpCodec[QValue]", HttpCodecTests[QValue].httpCodec)
  checkAll("Hash[QValue]", HashTests[QValue].hash)
  checkAll("BoundedEnumerable[QValue]", BoundedEnumerableTests[QValue].boundedEnumerable)

  test("sort by descending q-value") {
    forAll { (x: QValue, y: QValue) =>
      x.thousandths > y.thousandths ==> (x > y)
    }
  }

  test("fromDouble should be consistent with fromThousandths") {
    (0 to 1000).foreach { i =>
      assertEquals(fromDouble(i / 1000.0), fromThousandths(i))
    }
  }

  test("fromString should be consistent with fromThousandths") {
    (0 to 1000).foreach { i =>
      assertEquals(fromString((i / 1000.0).toString), fromThousandths(i))
    }
  }

  test("literal syntax should be consistent with successful fromDouble") {
    assertEquals(fromDouble(1.0), Right(qValue"1.0"))
    assertEquals(fromDouble(0.5), Right(qValue"0.5"))
    assertEquals(fromDouble(0.0), Right(qValue"0.0"))
  }

  test("literal syntax should reject invalid values") {
    assert(
      compileErrors {
        """qValue"2.0" // doesn't compile: out of range"""
      }.nonEmpty
    )

    assert(
      compileErrors {
        """qValue"invalid" // doesn't compile, not parsable as a double"""
      }.nonEmpty
    )
  }
}
