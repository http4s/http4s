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

import cats.kernel.laws.discipline.{BoundedEnumerableTests, HashTests, OrderTests}
import org.http4s.laws.discipline.HttpCodecTests

class QValueSpec extends Http4sSpec {
  import QValue._

  checkAll("Order[QValue]", OrderTests[QValue].order)
  checkAll("HttpCodec[QValue]", HttpCodecTests[QValue].httpCodec)
  checkAll("Hash[QValue]", HashTests[QValue].hash)
  checkAll("BoundedEnumerable[QValue]", BoundedEnumerableTests[QValue].boundedEnumerable)

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
    Right(qValue"1.0") must_== fromDouble(1.0)
    Right(qValue"0.5") must_== fromDouble(0.5)
    Right(qValue"0.0") must_== fromDouble(0.0)

  }

  "literal syntax should reject invalid values" in {
    import org.specs2.execute._, Typecheck._
    import org.specs2.matcher.TypecheckMatchers._

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
