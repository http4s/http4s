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

import cats.Hash
import cats.kernel.laws.discipline._
import cats.syntax.all._
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Prop.propBoolean

import java.util.Locale

class MethodSuite extends Http4sSuite {
  import Method._

  checkAll("Hash[Method]", HashTests[Method].hash)
  checkAll("Order[Method]", OrderTests[Method].order)
  checkAll("Hash[Method]", SerializableTests.serializable(Hash[Method]))

  test("parses own string rendering to equal value") {
    forAll(genToken) { token =>
      fromString(token).map(_.renderString) == Right(token)
    }
  }

  test("only tokens are valid methods") {
    forAll(genNonToken) { nonToken =>
      fromString(nonToken).isLeft
    }
  }

  test("parse a whole input string") { // see #3680
    val genAlmostToken = {
      val genNonToken =
        Gen
          .oneOf(
            Gen.choose('\u0000', '\u001F'),
            Gen.oneOf("()<>@,;:\\\"/[]?={} \t\u007F"),
          )
          .map(_.toString)

      for {
        tokenHead <- genToken
        nonToken <- genNonToken
        tokenTail <- Gen.option(genToken).map(_.orEmpty)
      } yield tokenHead + nonToken + tokenTail
    }
    forAllNoShrink(genAlmostToken) { almostToken =>
      fromString(almostToken).left.map((_: ParseFailure).sanitized) == Left("Invalid method")
    }
  }

  test("name is case sensitive") {
    forAll { (m: Method) =>
      val upper = m.name.toUpperCase(Locale.ROOT)
      val lower = m.name.toLowerCase(Locale.ROOT)
      (upper != lower) ==> { fromString(upper) != fromString(lower) }
    }
  }

  test("methods are equal by name") {
    forAll { (m: Method) =>
      Method.fromString(m.name) == Right(m)
    }
  }

  test("safety implies idempotence") {
    assert(Method.all.filter(_.isSafe).forall(_.isIdempotent))
  }
}
