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

class RunSubParserSpec extends TestParserSpec {

  class SubParser(val input: ParserInput) extends Parser {
    def IntNumber = rule {
      capture(oneOrMore(CharPredicate.Digit)) ~> (_.toInt)
    }
  }

  abstract class ParserWithSubParser extends TestParser1[Int] {
    def InputLine = rule {
      oneOrMore(runSubParser(new SubParser(_).IntNumber)).separatedBy(',') ~ EOI ~> (_.sum)
    }
  }

  "`runSubParser`" should {
    "work as expected" in new ParserWithSubParser {
      def targetRule = InputLine

      "12" must beMatchedWith(12)
      "43,8" must beMatchedWith(51)

      "1234,a" must beMismatchedWithErrorMsg(
        """Invalid input 'a', expected IntNumber (line 1, column 6):
          |1234,a
          |     ^
          |
          |1 rule mismatched at error location:
          |  /InputLine/ +:-5 / runSubParser /IntNumber/ capture / + / Digit:<CharPredicate>
          |""")
    }
  }
}
