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

class AcceptCHSuite extends HeaderLaws {
  checkAll("Accept-CH", headerLaws[`Accept-CH`])

  test("parse should fail with invalid header tokens") {
    assert(`Accept-CH`.parse("!@#$%").isLeft)
  }
  test("parse should succeed with single header") {
    assert(`Accept-CH`.parse("Viewport-Width").isRight)
  }
  test("parse should succeed with multiple comma-separated headers") {
    assert(`Accept-CH`.parse("Viewport-Width, Width").isRight)
  }
  test("parse should succeed with multiple headers and no whitespace") {
    assert(`Accept-CH`.parse("Viewport-Width,Width").isRight)
  }
  test("parse should succeed with no headers") {
    assert(`Accept-CH`.parse("").isRight)
  }
}
