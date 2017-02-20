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

class CalculatorSpec extends TestParserSpec {

  // format: OFF
  abstract class Calculator extends TestParser1[Int] {
    def InputLine = rule { Expression ~ EOI }

    def Expression: Rule1[Int] = rule {
      Term ~ zeroOrMore(
        '+' ~ Term ~> ((_: Int) + _)
      | '-' ~ Term ~> ((_: Int) - _))
    }

    def Term = rule {
      Factor ~ zeroOrMore(
        '*' ~ Factor ~> ((_: Int) * _)
      | '/' ~ Factor ~> ((_: Int) / _))
    }

    def Factor = rule { Number | Parens }

    def Parens = rule { '(' ~ Expression ~ ')' }

    def Number = rule { capture(Digits) ~> (_.toInt) }

    def Digits = rule { oneOrMore(CharPredicate.Digit) }
  }
  // format: ON

  "The Calculator parser" should {
    "successfully evaluate simple calculator expression" in new Calculator {
      def targetRule = InputLine

      "1" must beMatchedWith(1)
      "1+2" must beMatchedWith(3)
      "1+2*3" must beMatchedWith(7)
      "1*2+3" must beMatchedWith(5)
      "1*(2+3)" must beMatchedWith(5)
      "10*((2+3))" must beMatchedWith(50)
      "(2+3)*(80-60)" must beMatchedWith(100)
      "2*8/2+16/16-16/(1+3)" must beMatchedWith(5)

      "*1" must beMismatchedWithErrorMsg(
        """Invalid input '*', expected Number or Parens (line 1, column 1):
          |*1
          |^
          |
          |2 rules mismatched at error location:
          |  /InputLine/ /Expression/ /Term/ /Factor/ | /Number/ capture /Digits/ + / Digit:<CharPredicate>
          |  /InputLine/ /Expression/ /Term/ /Factor/ | /Parens/ '('
          |""")

      "()" must beMismatchedWithErrorMsg(
        """Invalid input ')', expected Number or Parens (line 1, column 2):
          |()
          | ^
          |
          |2 rules mismatched at error location:
          |  ...xpression/ /Term/ /Factor/ |:-1 /Parens/ /Expression/ /Term/ /Factor/ | /Number/ capture /Digits/ + / Digit:<CharPredicate>
          |  /InputLine/ /Expression/ /Term/ /Factor/ |:-1 /Parens/ /Expression/ /Term/ /Factor/ | /Parens/ '('
          |""")

      "1+2)" must beMismatchedWithErrorMsg(
        """Invalid input ')', expected '/', '+', '*', 'EOI', '-' or Digit (line 1, column 4):
          |1+2)
          |   ^
          |
          |6 rules mismatched at error location:
          |  /InputLine/ /Expression/ *:-2 / |:-2 /Term/ /Factor/ |:-1 /Number/ capture:-1 /Digits/ +:-1 / Digit:<CharPredicate>
          |  /InputLine/ /Expression/ *:-2 / |:-2 /Term/ * / | / '*'
          |  /InputLine/ /Expression/ *:-2 / |:-2 /Term/ * / | / '/'
          |  /InputLine/ /Expression/ *:-2 / | / '+'
          |  /InputLine/ /Expression/ *:-2 / | / '-'
          |  /InputLine/ 'EOI'
          |""")

      "(1+)2" must beMismatchedWithErrorMsg(
        """Invalid input ')', expected Number or Parens (line 1, column 4):
          |(1+)2
          |   ^
          |
          |2 rules mismatched at error location:
          |  ...Term/ /Factor/ |:-3 /Parens/ /Expression/ *:-1 / |:-1 /Term/ /Factor/ | /Number/ capture /Digits/ + / Digit:<CharPredicate>
          |  /InputLine/ /Expression/ /Term/ /Factor/ |:-3 /Parens/ /Expression/ *:-1 / |:-1 /Term/ /Factor/ | /Parens/ '('
          |""")
    }
  }
}
