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

class SecFetchUserSuite extends HeaderLaws {
  checkAll("Sec-Fetch-User", headerLaws[`Sec-Fetch-User`])

  test("render should render all directives") {
    assert(`Sec-Fetch-User`(`Sec-Fetch-User`.`?1`).renderString == "Sec-Fetch-User: ?1")
  }

  test("parse should parse all directives") {
    assert(`Sec-Fetch-User`.parse("?1") == Right(`Sec-Fetch-User`.`?1`))
  }

  test("parse should fail with invalid directives") {
    assert(`Sec-Fetch-User`.parse("invalid").isLeft)
  }
}
