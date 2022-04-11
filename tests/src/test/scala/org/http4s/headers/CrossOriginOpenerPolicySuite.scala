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

import cats.syntax.either._
import org.http4s.laws.discipline.arbitrary._

class CrossOriginOpenerPolicySuite extends HeaderLaws {
  checkAll("Cross-Origin-Opener-Policy-Suite", headerLaws[`Cross-Origin-Opener-Policy`])

  test("parsing unsafe-none into UnsafeNone") {
    assertEquals(
      `Cross-Origin-Opener-Policy`.parser.parseAll("unsafe-none"),
      `Cross-Origin-Opener-Policy`.UnsafeNone.asRight,
    )
  }

  test("parsing same-origin-allow-popups into SameOriginAllowPopups") {
    assertEquals(
      `Cross-Origin-Opener-Policy`.parser.parseAll("same-origin-allow-popups"),
      `Cross-Origin-Opener-Policy`.SameOriginAllowPopups.asRight,
    )
  }

  test("parsing same-origin into SameOrigin") {
    assertEquals(
      `Cross-Origin-Opener-Policy`.parser.parseAll("same-origin"),
      `Cross-Origin-Opener-Policy`.SameOrigin.asRight,
    )
  }
}
