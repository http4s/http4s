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

import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.header._
import org.scalacheck.Prop

class SecFetchSiteSuite extends HeaderLaws {
  checkAll("Sec-Fetch-Site", headerLaws[`Sec-Fetch-Site`])

  test("render should render all directives") {
    Prop.forAll { (a: `Sec-Fetch-Site`) =>
      `Sec-Fetch-Site`(a).renderString == s"Sec-Fetch-Site: ${a.value}"
    }
  }

  test("parse should parse all directives") {
    Prop.forAll { (a: `Sec-Fetch-Site`) =>
      `Sec-Fetch-Site`.parse(a.value) == Right(a)
    }
  }

  test("parse should fail with invalid directives") {
    assert(`Sec-Fetch-Site`.parse("invalid").isLeft)
  }

  test("parse should fail with an empty string") {
    assert(`Sec-Fetch-Site`.parse("").isLeft)
  }
}
