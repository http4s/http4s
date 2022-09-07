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

import cats.data.NonEmptyList
import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.header._
import org.scalacheck.Prop

class ReferrerPolicySuite extends HeaderLaws {
  checkAll("ReferrerPolicy", headerLaws[`Referrer-Policy`])

  import `Referrer-Policy`.{Directive, UnknownPolicy}

  test("render should render unknown directives") {
    assertEquals(
      `Referrer-Policy`(
        UnknownPolicy.unsafeFromString("unknown-a"),
        UnknownPolicy.unsafeFromString("unknown-b"),
      ).renderString,
      "Referrer-Policy: unknown-a, unknown-b",
    )
  }

  test("parse should parse unknown directives") {
    val unknownA = UnknownPolicy.unsafeFromString("unknown-a")
    val unknownB = UnknownPolicy.unsafeFromString("unknown-b")
    assertEquals(
      `Referrer-Policy`.parse("unknown-a, unknown-b").map(_.values),
      Right(NonEmptyList.of(unknownA, unknownB)),
    )
  }

  test("parse should parse directives in any letter case") {
    Prop.forAll { (a: Directive) =>
      val expected = Right(NonEmptyList.one(a))
      (`Referrer-Policy`.parse(a.value.toString.toLowerCase).map(_.values) == expected) &&
      (`Referrer-Policy`.parse(a.value.toString.toUpperCase).map(_.values) == expected)
    }
  }

  test("parse should fail with an empty string") {
    assert(`Referrer-Policy`.parse("").isLeft)
  }

  test("parse should fail with invalid directives") {
    assert(`Referrer-Policy`.parse("abc-012").isLeft)
    assert(`Referrer-Policy`.parse("origin, abc-012, unsafe-url").isLeft)
  }

  test("Directive.fromString should parse all known directives") {
    Prop.forAll { (a: Directive) =>
      Directive.fromString(a.value.toString) == Right(a)
    }
  }

  test("Directive.fromString should parse unknown directives") {
    val unknown = UnknownPolicy.unsafeFromString("unknown-policy")
    assertEquals(Directive.fromString(unknown.value.toString), Right(unknown))
  }

  test("Directive.fromString should fail with invalid directives") {
    assert(Directive.fromString("abc-012").isLeft)
  }
}
