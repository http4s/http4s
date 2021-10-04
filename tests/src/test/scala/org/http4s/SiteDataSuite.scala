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

import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Prop

class SiteDataSuite extends Http4sSuite {
  test("render should render directives quoted") {
    Prop.forAll { (a: SiteData) =>
      a.renderString == s""""${a.value}""""
    }
  }

  test("parse should parse known directives") {
    Prop.forAll { (a: SiteData) =>
      SiteData.parse(s""""${a.value}"""") == Right(a)
    }
  }

  test("parse should parse known directives in any letter case") {
    Prop.forAll { (a: SiteData) =>
      (SiteData.parse(s""""${a.value.toLowerCase}"""") == Right(a)) &&
      (SiteData.parse(s""""${a.value.toUpperCase}"""") == Right(a))
    }
  }

  test("parse should fail with an empty string") {
    assert(SiteData.parse("").isLeft)
  }

  test("parse should fail with an empty quoted string") {
    assert(SiteData.parse("""""""").isLeft)
  }

  test("parse should fail with an unknown directive") {
    assert(SiteData.parse(""""unknown"""").isLeft)
  }

  test("parse should fail with an invalid directive (not quoted)") {
    assert(SiteData.parse("cookies").isLeft)
  }
}
