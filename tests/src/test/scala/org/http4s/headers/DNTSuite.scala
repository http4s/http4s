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
package headers

import org.scalacheck.Gen
import org.scalacheck.Arbitrary
import cats.syntax.either._

class DNTSuite extends HeaderLaws {
  val dntGen = Gen.option(Gen.oneOf(true, false)).map(DNT(_))
  implicit val arbDnt: Arbitrary[DNT] = Arbitrary[DNT](dntGen)
  checkAll("DNT", headerLaws[DNT])

  test("parsing null into None") {
    assertEquals(DNT.parser.parseAll("null"), DNT(None).asRight)
  }

  test("parsing 1 into Some(true)") {
    assertEquals(DNT.parser.parseAll("1"), DNT(Some(true)).asRight)
  }

  test("parsing 0 into Some(false)") {
    assertEquals(DNT.parser.parseAll("0"), DNT(Some(false)).asRight)
  }
}
