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

class KeepAliveSuite extends HeaderLaws {
  checkAll("Keep-Alive", headerLaws[`Keep-Alive`])

  test("invalid (empty) Keep-Alives should result in failure") {
    assertEquals(
      `Keep-Alive`(None, None, List.empty),
      ParseResult.fail("Invalid Keep-Alive header", "All fields of Keep-Alive were empty"),
    )
  }

  test("parse keep-alive with only timeout") {
    assertEquals(
      Header[`Keep-Alive`].parse("timeout=3"),
      `Keep-Alive`(Some(3), None, List.empty),
    )
  }

  test("parse keep-alive with only max") {
    assertEquals(
      Header[`Keep-Alive`].parse("max=33"),
      `Keep-Alive`(None, Some(33), List.empty),
    )
  }

  test("parse keep-alive with timeout and max") {
    assertEquals(
      Header[`Keep-Alive`].parse("timeout=3, max=33"),
      `Keep-Alive`(Some(3), Some(33), List.empty),
    )
  }

  test("parse keep-alive with timeout, max, and extensions with quoted string") {
    assertEquals(
      Header[`Keep-Alive`].parse("""timeout=3, max=33, extKey="il""""),
      `Keep-Alive`(Some(3), Some(33), List(("extKey", Some("il")))),
    )
  }

  test(
    "parse keep-alive with timeout, max, and extensions with 3 keys (extKey as a token) some have values and one does not"
  ) {
    assertEquals(
      Header[`Keep-Alive`].parse("""timeout=3, max=33, extKey=il, nextKey="pi", thirdKey"""),
      `Keep-Alive`(
        Some(3),
        Some(33),
        List(("extKey", Some("il")), ("nextKey", Some("pi")), ("thirdKey", None)),
      ),
    )
  }

  test(
    "parse keep-alive with only timeout, max, and extensions where the token is missing the '='"
  ) {
    assertEquals(
      Header[`Keep-Alive`].parse("timeout=3, max=33, extKey"),
      `Keep-Alive`(Some(3), Some(33), List(("extKey", None))),
    )
  }

  test(
    "parse keep-alive with only timeout, max, and extensions with multiple timeout values throwing these extras away"
  ) {
    assertEquals(
      Header[`Keep-Alive`].parse("timeout=3, max=33, extKey, timeout=8"),
      `Keep-Alive`(Some(3), Some(33), List(("extKey", None))),
    )
  }

  test(
    "parse keep-alive with only timeout, max, and extensions with multiple max values throwing these extras away"
  ) {
    assertEquals(
      Header[`Keep-Alive`].parse("timeout=3, max=33, extKey=1, max=8"),
      `Keep-Alive`(Some(3), Some(33), List(("extKey", Some("1")))),
    )
  }

  test(
    "parse keep-alive fails if extensions contain reserved 'token'"
  ) {
    assertEquals(
      Header[`Keep-Alive`].parse("timeout=3, max=33, token=foo, max=8"),
      Left(
        ParseFailure(
          "Invalid Keep-Alive header",
          "Reserved token 'token' was found in the extensions.",
        )
      ),
    )
  }
}
