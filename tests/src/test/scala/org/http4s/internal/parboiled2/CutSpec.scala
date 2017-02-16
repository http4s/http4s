/*
 * Copyright (C) 2009-2013 Mathias Doenitz, Alexander Myltsev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.internal.parboiled2

class CutSpec extends TestParserSpec {

  "The `~!~` (cut) operator" should {
    "work as expected" in new TestParser0 {
      def targetRule = rule { foo ~ EOI }
      def foo = rule { "abc" | "a" ~!~ "de" | "axy" }

      "abc" must beMatched
      "ade" must beMatched
      "axy" must beMismatched

      "axy" must beMismatchedWithErrorMsg(
        """Invalid input 'x', expected 'b' or 'd' (line 1, column 2):
          |axy
          | ^
          |
          |2 rules mismatched at error location:
          |  /targetRule/ /foo/ |:-1 / "abc":-1 / 'b'
          |  /targetRule/ /foo/ |:-1 / cut:-1 / "de" / 'd'
          |""")
    }
  }
}
