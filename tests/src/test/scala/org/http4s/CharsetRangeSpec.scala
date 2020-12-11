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

import cats.kernel.laws.discipline.EqTests
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
      forAll(arbitrary[CharsetRange.Atom]) { range =>
        range.matches(range.charset)
      }
    }

    "not be satisfied by any other charsets" in {
      prop { (range: CharsetRange.Atom, cs: Charset) =>
        range.charset != cs ==> !range.matches(cs)
      }
    }
  }

  checkAll("CharsetRange", EqTests[CharsetRange].eqv)
}
