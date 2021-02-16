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
package parser

import cats.syntax.all._
import org.http4s.internal.parboiled2._
import org.http4s.laws.discipline.ArbitraryInstances._
import org.scalacheck.{Gen, Prop}

final case class BasicRulesParser(input: ParserInput) extends Parser with Rfc2616BasicRules {
  def ListSepOnly = rule(ListSep ~ EOI)
}

class Rfc2616ParserSpec extends Http4sSuite {

  test("Rfc2616 parser should Parse chars") {
    Prop.forAll(genChar) { (l: Char) =>
      Either.fromTry(BasicRulesParser(l.toString).Char.run()) == Right(())
    }
  }
  test("Rfc2616 parser should Parse octet") {
    Prop.forAll(genOctet) { (l: Char) =>
      Either.fromTry(BasicRulesParser(l.toString).Octet.run()) == Right(())
    }
  }
  test("Rfc2616 parser should Parse crlf") {
    Prop.forAll(genCrLf) { (l: String) =>
      Either.fromTry(BasicRulesParser(l).CRLF.run()) == Right(())
    }
  }
  test("Rfc2616 parser should Parse lws") {
    Prop.forAll(genLws) { (l: String) =>
      Either.fromTry(BasicRulesParser(l).LWS.run()) == Right(())
    }
  }
  test("Rfc2616 parser should Parse text") {
    Prop.forAll(genText) { (l: String) =>
      Either.fromTry(BasicRulesParser(l).Text.run()) == Right(())
    }
  }
  test("Rfc2616 parser should Parse quoted pair") {
    Prop.forAll(genQuotedPair) { (l: String) =>
      Either.fromTry(BasicRulesParser(l).QuotedPair.run()).map(Some.apply) == Right(l.lastOption)
    }
  }
  test("Rfc2616 parser should Parse quoted text") {
    Prop.forAll(genQDText) { (l: String) =>
      Either.fromTry(BasicRulesParser(l).QDText.run()).map(Some.apply) == Right(l.headOption)
    }
  }
  test("Rfc2616 parser should Parse quoted string") {
    Prop.forAll(genQuotedString) { (l: String) =>
      val withoutQuotes = l.substring(1, l.length - 1)
      val value = Either.fromTry(BasicRulesParser(l).QuotedString.run()).map(Some.apply)
      (value == Right(Some(withoutQuotes))) || (value == Right(
        withoutQuotes.lastOption.map(_.toString))) || (value == Right(Some("")))

    }
  }
  test("Rfc2616 parser should Parse list separator") {
    Prop.forAll(genListSep) { listSep =>
      Either.fromTry(BasicRulesParser(listSep).ListSepOnly.run()).isRight
    }
  }
  test("Rfc2616 parser should Not parse list separator without a comma") {
    Prop.forAll(genOptWs) { listSep =>
      Either.fromTry(BasicRulesParser(listSep).ListSepOnly.run()).isLeft
    }
  }
  test("Rfc2616 parser should Not parse list separator with more than one comma") {
    val genBadListSep =
      for {
        initListSep <- genListSep
        moreCommas <- Gen.nonEmptyListOf {
          Gen.sequence[List[String], String](List(Gen.const(","), genOptWs))
        }
      } yield initListSep + moreCommas.flatten.mkString

    Prop.forAll(genBadListSep) { listSep =>
      Either.fromTry(BasicRulesParser(listSep).ListSepOnly.run()).isLeft
    }
  }
  test("Rfc2616 parser should Not parse a non-comma list separator") {
    val genBadListSep = Gen.asciiPrintableChar.filterNot(_ == ',').map(_.toString)

    Prop.forAll(genBadListSep) { listSep =>
      Either.fromTry(BasicRulesParser(listSep).ListSepOnly.run()).isLeft
    }
  }

}
