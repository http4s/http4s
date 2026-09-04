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

class ClearSiteDataSuite extends HeaderLaws {
  checkAll("ClearSiteData", headerLaws[`Clear-Site-Data`])

  import `Clear-Site-Data`.{Directive, UnknownType}

  test("render should render unknown directives") {
    assertEquals(
      `Clear-Site-Data`(
        UnknownType.unsafeFromString("unknownA"),
        UnknownType.unsafeFromString("unknownB"),
      ).renderString,
      """Clear-Site-Data: "unknownA", "unknownB"""",
    )
  }

  test("parse should parse unknown directives") {
    val unknownA = UnknownType.unsafeFromString("unknownA")
    val unknownB = UnknownType.unsafeFromString("unknownB")
    val expected = Right(NonEmptyList.of(unknownA, unknownB))
    assertEquals(`Clear-Site-Data`.parse(""""unknownA", "unknownB"""").map(_.values), expected)
  }

  test("parse should fail with an empty string") {
    assert(`Clear-Site-Data`.parse("").isLeft)
  }

  test("parse should fail with invalid directives (not quoted)") {
    assert(`Clear-Site-Data`.parse("cookies").isLeft)
    assert(`Clear-Site-Data`.parse(""""cache", cookies, "storage"""").isLeft)
  }

  test("Directive.fromString should parse all known directives") {
    Prop.forAll { (a: Directive) =>
      Directive.fromString(s""""${a.value}"""") == Right(a)
    }
  }

  test("Directive.fromString should parse unknown directives") {
    val unknown = UnknownType.unsafeFromString("unknown")
    assertEquals(Directive.fromString(s""""${unknown.value}""""), Right(unknown))
  }

  test("Directive.fromString should fail with invalid directives (not quoted)") {
    assert(Directive.fromString("cookies").isLeft)
  }
}
