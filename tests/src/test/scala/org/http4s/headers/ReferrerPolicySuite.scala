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
import org.http4s.syntax.header._
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Prop

class ReferrerPolicySuite extends HeaderLaws {
  checkAll("ReferrerPolicy", headerLaws[`Referrer-Policy`])

  import `Referrer-Policy`.{Directive, UnknownPolicy}

  test("render should render a single directive") {
    Prop.forAll { (a: Directive) =>
      `Referrer-Policy`(a).renderString == s"Referrer-Policy: ${a.value}"
    }
  }

  test("render should render multiple directives") {
    Prop.forAll { (a: Directive, b: Directive, c: Directive) =>
      val expected = s"Referrer-Policy: ${a.value}, ${b.value}, ${c.value}"
      `Referrer-Policy`(a, b, c).renderString == expected
    }
  }

  test("parse should parse a single directive") {
    Prop.forAll { (a: Directive) =>
      `Referrer-Policy`.parse(s"${a.value}").map(_.values) == Right(NonEmptyList.one(a))
    }
  }

  test("parse should parse multiple directives") {
    Prop.forAll { (a: Directive, b: Directive, c: Directive) =>
      `Referrer-Policy`
        .parse(s"${a.value}, ${b.value}, ${c.value}")
        .map(_.values) == Right(NonEmptyList.of(a, b, c))
    }
  }

  test("parse should parse unknown directives") {
    assertEquals(
      `Referrer-Policy`.parse("unknown-policy-a, unknown-policy-b").map(_.values),
      Right(NonEmptyList.of(UnknownPolicy("unknown-policy-a"), UnknownPolicy("unknown-policy-b")))
    )
  }

  test("parse should fail with a single invalid directive") {
    assert(`Referrer-Policy`.parse("abc-012").isLeft)
  }

  test("parse should fail when some directives are invalid") {
    assert(`Referrer-Policy`.parse("origin, abc-012, unsafe-url").isLeft)
  }
}
