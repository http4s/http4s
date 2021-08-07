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

import cats.kernel.laws.discipline.OrderTests
import org.http4s.headers._
import org.http4s.laws.discipline.ArbitraryInstances._

class HeaderSuite extends munit.DisciplineSuite {
  test("Headers should Equate same headers") {
    val h1 = `Content-Length`.unsafeFromLong(4)
    val h2 = `Content-Length`.unsafeFromLong(4)

    assertEquals(h1, h2)
    assertEquals(h2, h1)
  }

  test("Headers should not equal different headers") {
    val h1 = `Content-Length`.unsafeFromLong(4)
    val h2 = `Content-Length`.unsafeFromLong(5)

    assert(!(h1 == h2))
    assert(!(h2 == h1))
  }

  /*
  test("rendered length should is rendered length including \\r\\n") {
    forAll { (h: Header.Raw) =>
      assertEquals(
        h.render(new StringWriter << "\r\n")
          .result
          .getBytes(ISO_8859_1)
          .length
          .toLong,
        h.renderedLength)
    }
  }
   */

  test("Order instance for Header should be lawful") {
    checkAll("Order[Header]", OrderTests[Header.Raw].order)
  }
}
