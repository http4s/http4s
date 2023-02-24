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

import org.http4s.implicits.http4sSelectSyntaxOne

class XFrameOptionsSuite extends Http4sSuite {

  test("render should create and header with the correct value") {
    assertEquals(
      `X-Frame-Options`.parse("\"deny\"").map(_.renderString),
      ParseResult.success("X-Frame-Options: DENY"),
    )

    assertEquals(
      `X-Frame-Options`.parse("\"sameorigin\"").map(_.renderString),
      ParseResult.success("X-Frame-Options: SAMEORIGIN"),
    )
  }

  test("parse should fail on a header with invalid value") {
    assert(`X-Frame-Options`.parse("\"invalid\"").map(_.renderString).isLeft)
  }

  test("parse should be case insensitive") {
    assertEquals(
      `X-Frame-Options`.parse("\"DEnY\"").map(_.renderString),
      ParseResult.success("X-Frame-Options: DENY"),
    )

    assertEquals(
      `X-Frame-Options`.parse("\"SAMeORIGIN\"").map(_.renderString),
      ParseResult.success("X-Frame-Options: SAMEORIGIN"),
    )
  }
}
