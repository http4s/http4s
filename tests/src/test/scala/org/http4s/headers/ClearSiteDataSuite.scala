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

class ClearSiteDataSuite extends HeaderLaws {
  checkAll("ClearSiteData", headerLaws[`Clear-Site-Data`])

  import `Clear-Site-Data`.Directive

  test("render should render a single directive") {
    Prop.forAll { (a: Directive) =>
      `Clear-Site-Data`(a).renderString == s"""Clear-Site-Data: "${a.value}""""
    }
  }

  test("render should render multiple directives") {
    assertEquals(
      `Clear-Site-Data`(
        `Clear-Site-Data`.`*`,
        `Clear-Site-Data`.cache,
        `Clear-Site-Data`.cookies,
        `Clear-Site-Data`.storage,
        `Clear-Site-Data`.executionContexts
      ).renderString,
      """Clear-Site-Data: "*", "cache", "cookies", "storage", "executionContexts""""
    )
  }

  test("parse should parse a single directive") {
    Prop.forAll { (a: Directive) =>
      `Clear-Site-Data`.parse(s""""${a.value}"""").map(_.values) == Right(NonEmptyList.one(a))
    }
  }

  test("parse should parse multiple directives") {
    assertEquals(
      `Clear-Site-Data`
        .parse(""""*", "cache", "cookies", "storage", "executionContexts"""")
        .map(_.values),
      Right(
        NonEmptyList.of(
          `Clear-Site-Data`.`*`,
          `Clear-Site-Data`.cache,
          `Clear-Site-Data`.cookies,
          `Clear-Site-Data`.storage,
          `Clear-Site-Data`.executionContexts
        )
      )
    )
  }

  test("parse should fail with an empty string") {
    assert(`Clear-Site-Data`.parse("").isLeft)
  }

  test("parse should fail with an empty quoted string") {
    assert(`Clear-Site-Data`.parse("""""""").isLeft)
  }

  test("parse should fail with a single unknown directive") {
    assert(`Clear-Site-Data`.parse(""""unknown"""").isLeft)
  }

  test("parse should fail with a single invalid directive (not quoted)") {
    assert(`Clear-Site-Data`.parse("cookies").isLeft)
  }

  test("parse should fail when some directives are unknown") {
    assert(`Clear-Site-Data`.parse(""""cache", "unknown", "storage", "executionContexts"""").isLeft)
  }

  test("parse should fail when some directives are invalid (not quoted)") {
    assert(`Clear-Site-Data`.parse(""""cache", cookies, "storage", "executionContexts"""").isLeft)
  }
}
