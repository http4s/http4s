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

class XContentTypeOptionsSuite extends Http4sSuite {
  test("render should create and header with no-sniff") {
    assertEquals(
      `X-Content-Type-Options`.parse("\"nosniff\"").map(_.renderString),
      ParseResult.success("X-Content-Type-Options: nosniff"),
    )
  }

  test("parse should fail on a header with invalid value") {
    assert(`X-Content-Type-Options`.parse("invalid").map(_.renderString).isLeft)
  }

  test("parse should create header with uppercase value") {
    assertEquals(
      `X-Content-Type-Options`.parse("\"NOSNIFF\"").map(_.renderString),
      ParseResult.success("X-Content-Type-Options: nosniff"),
    )
  }
}
