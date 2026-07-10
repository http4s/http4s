/*
 * Copyright 2026 http4s.org
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

class AcceptQuerySuite extends HeaderLaws {
  checkAll("Accept-Query", headerLaws[`Accept-Query`])

  test("parse should fail on invalid formats") {
    assert(`Accept-Query`.parse("applic/*/").isLeft)
  }

  test("parse should succeed on wildcard formats") {
    assert(`Accept-Query`.parse("application/*").isRight)
    assert(`Accept-Query`.parse("text/*").isRight)
    assert(`Accept-Query`.parse("*/*").isRight)
  }

  test("parse should succeed on many comma separated values") {
    assert(`Accept-Query`.parse("application/*, text/*").isRight)
  }

  test("parse and render quoted and unquoted values (Structured Fields)") {
    // Standard unquoted
    val unquoted = "application/sql"
    val parsedUnquoted = `Accept-Query`.parse(unquoted)
    assert(parsedUnquoted.isRight)
    assertEquals(parsedUnquoted.toOption.get.values, List(MediaType.application.sql))
    assertEquals(parsedUnquoted.toOption.get.renderString, s"Accept-Query: $unquoted")

    // Quoted string (used for names containing + or starting with digit, or general Structured Fields strings)
    val quoted = "\"application/jsonpath\""
    val parsedQuoted = `Accept-Query`.parse(quoted)
    assert(parsedQuoted.isRight)
    assertEquals(parsedQuoted.toOption.get.values, List(new MediaType("application", "jsonpath")))
    // application/jsonpath is a valid token, so it renders unquoted
    assertEquals(parsedQuoted.toOption.get.renderString, "Accept-Query: application/jsonpath")

    // Quoted string with suffix (+)
    val suffixedQuoted = "\"application/ld+json\""
    val parsedSuffixed = `Accept-Query`.parse(suffixedQuoted)
    assert(parsedSuffixed.isRight)
    assertEquals(parsedSuffixed.toOption.get.values, List(new MediaType("application", "ld+json")))
    // application/ld+json contains +, which is not a valid token character, so it renders quoted!
    assertEquals(parsedSuffixed.toOption.get.renderString, "Accept-Query: \"application/ld+json\"")

    // Mix of quoted/unquoted and parameters
    val mixed = "\"application/jsonpath\", application/sql;charset=\"UTF-8\""
    val parsedMixed = `Accept-Query`.parse(mixed)
    assert(parsedMixed.isRight)
    assertEquals(
      parsedMixed.toOption.get.values,
      List(
        new MediaType("application", "jsonpath"),
        MediaType.application.sql.withExtensions(Map("charset" -> "UTF-8")),
      ),
    )
    assertEquals(
      parsedMixed.toOption.get.renderString,
      "Accept-Query: application/jsonpath, application/sql;charset=\"UTF-8\"",
    )
  }
}
