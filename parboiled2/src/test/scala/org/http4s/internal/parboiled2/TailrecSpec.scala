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

// test verifying the effectiveness of our workaround for https://issues.scala-lang.org/browse/SI-8657
class TailrecSpec extends TestParserSpec {

  abstract class TailrecParser extends TestParser0 {
    def InputLine = rule {
      oneOrMore('x') ~ EOI | zeroOrMore('x') ~ 'y' ~ EOI
    }
  }

  "The TailrecParser parser" should {
    "be able to match 100,000 chars without overflowing the stack" in new TailrecParser {
      def targetRule = InputLine

      val chars = Array.fill(100000)('x')
      chars(99999) = 'y'
      new String(chars) must beMatched
    }
  }
}
