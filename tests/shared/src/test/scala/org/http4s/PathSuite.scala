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

import cats.kernel.laws.discipline._
import org.http4s.Uri.Path
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Prop._

class PathSuite extends Http4sSuite {
  checkAll("Order[Path]", OrderTests[Path].order)
  checkAll("Semigroup[Path]", SemigroupTests[Path].semigroup)
  checkAll("Hash[Path]", HashTests[Path].hash)

  test("merge should be producing a new Path according to rfc3986 5.2.3") {
    forAll { (a: Path, b: Path) =>
      if (a.endsWithSlash)
        assertEquals(a.merge(b), Path(a.segments ++ b.segments, a.absolute, b.endsWithSlash))
      else
        assertEquals(
          a.merge(b),
          Path(
            (if (a.segments.nonEmpty) a.segments.init else Vector.empty) ++ b.segments,
            a.absolute,
            b.endsWithSlash,
          ),
        )
    }
  }
}
