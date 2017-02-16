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

import org.http4s.internal.parboiled2.support._

//// pure compile-time-only test
class DSLTest(val input: ParserInput) extends Parser {

  def ZeroOrMoreReduction_Checked: Rule[String :: HNil, String :: HNil] = ZeroOrMoreReduction
  def ZeroOrMoreReduction = rule { zeroOrMore(capture("0" - "9") ~> ((x: String, y) ⇒ x + y)) }

  def OptionalReduction_Checked: Rule[String :: HNil, String :: HNil] = OptionalReduction
  def OptionalReduction = rule { optional(capture("0" - "9") ~> ((x: String, y) ⇒ x + y)) }

  def OpsTest1_Checked: RuleN[Int :: Boolean :: String :: Int :: Boolean :: Int :: Boolean :: Array[Char] :: HNil] = OpsTest1
  def OpsTest1 = rule { ComplexRule ~> (_.toCharArray) }

  def OpsTest2_Checked: RuleN[Int :: Boolean :: String :: Int :: Boolean :: Int :: HNil] = OpsTest2
  def OpsTest2 = rule { ComplexRule ~> ((_, s) ⇒ s.length) ~> (_ + _) }

  def ComplexRule_Checked: RuleN[Int :: Boolean :: String :: Int :: Boolean :: Int :: Boolean :: String :: HNil] = ComplexRule
  def ComplexRule = rule { capture(DigitAndBool) ~ DigitAndBool ~ capture(DigitAndBool) }

  def DigitAndBool_Checked: Rule2[Int, Boolean] = DigitAndBool
  def DigitAndBool = rule { Digit ~ Bool }

  def Bool_Checked: Rule1[Boolean] = Bool
  def Bool = rule { BoolTrue | BoolFalse }

  def BoolTrue_Checked: Rule1[Boolean] = BoolTrue
  def BoolTrue = rule { str("true") ~ push(true) }

  def BoolFalse_Checked: Rule1[Boolean] = BoolFalse
  def BoolFalse = rule { str("false") ~ push(false) }

  def Digits_Checked: Rule1[Seq[Int]] = Digits
  def Digits = rule { oneOrMore(Digit) }

  def DigitsOrEmpty_Checked: Rule1[Seq[Int]] = DigitsOrEmpty
  def DigitsOrEmpty = rule { zeroOrMore(Digit) }

  def Digit_Checked: Rule1[Int] = Digit
  def Digit = rule { capture("0" - "9") ~> (_.toInt) }

  def DigitOptional_Checked: Rule1[Option[Int]] = DigitOptional
  def DigitOptional = rule { optional(Digit) }
}
