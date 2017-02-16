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

import org.specs2.specification.Scope
import org.specs2.specification.dsl.NoReferenceDsl
import org.specs2.mutable.Specification
import org.specs2.control.NoNumberOfTimes
import org.http4s.internal.parboiled2.support.Unpack
import org.http4s.internal.parboiled2.support._

abstract class TestParserSpec extends Specification with NoReferenceDsl with NoNumberOfTimes {
  type TestParser0 = TestParser[HNil, Unit]
  type TestParser1[T] = TestParser[T :: HNil, T]
  type TestParserN[L <: HList] = TestParser[L, L]

  // work-around for https://github.com/etorreborre/specs2/issues/514
  override def mutableLinkFragment(alias: String): mutableLinkFragment = ???
  override def mutableSeeFragment(alias: String): mutableSeeFragment = ???

  abstract class TestParser[L <: HList, Out](implicit unpack: Unpack.Aux[L, Out]) extends Parser with Scope {
    var input: ParserInput = _
    def errorFormatter: ErrorFormatter = new ErrorFormatter(showTraces = true)

    def targetRule: RuleN[L]

    def beMatched = beTrue ^^ (parse(_: String).isRight)
    def beMatchedWith(r: Out) = parse(_: String) === Right(r)
    def beMismatched = beTrue ^^ (parse(_: String).isLeft)
    def beMismatchedWithError(pe: ParseError) = parse(_: String).left.toOption.get === pe
    def beMismatchedWithErrorMsg(msg: String) =
      parse(_: String).left.toOption.map(formatError(_, errorFormatter)).get === msg.stripMargin

    def parse(input: String): Either[ParseError, Out] = {
      this.input = input
      import Parser.DeliveryScheme.Either
      targetRule.run()
    }
  }
}
