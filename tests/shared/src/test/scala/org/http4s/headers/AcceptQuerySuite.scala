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

class AcceptQuerySuite extends HeaderLaws {
  checkAll("Accept-Query", headerLaws[`Accept-Query`])

  test("parse should fail on invalid formats") {
    assert(`Accept-Query`.parse("applic/*/").isLeft)
    assert(`Accept-Query`.parse("").isLeft)
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
    assertEquals(parsedUnquoted.toOption.get.values, NonEmptyList.of(MediaType.application.sql))
    assertEquals(parsedUnquoted.toOption.get.renderString, s"Accept-Query: $unquoted")

    // Media type starting with a digit (must be quoted because sf-token must start with ALPHA or *)
    val digitQuoted = "\"3gpp/media\""
    val parsedDigit = `Accept-Query`.parse(digitQuoted)
    assert(parsedDigit.isRight)
    assertEquals(
      parsedDigit.toOption.get.values,
      NonEmptyList.of(new MediaType("3gpp", "media")),
    )
    // 3gpp/media is parsed from quoted string and rendered as standard media type
    assertEquals(parsedDigit.toOption.get.renderString, "Accept-Query: 3gpp/media")

    // Quoted string with suffix (+)
    val suffixedQuoted = "\"application/ld+json\""
    val parsedSuffixed = `Accept-Query`.parse(suffixedQuoted)
    assert(parsedSuffixed.isRight)
    assertEquals(
      parsedSuffixed.toOption.get.values,
      NonEmptyList.of(new MediaType("application", "ld+json")),
    )
    assertEquals(parsedSuffixed.toOption.get.renderString, "Accept-Query: application/ld+json")

    // Mix of quoted/unquoted and parameters
    val mixed = "\"application/jsonpath\", application/sql;charset=\"UTF-8\""
    val parsedMixed = `Accept-Query`.parse(mixed)
    assert(parsedMixed.isRight)
    assertEquals(
      parsedMixed.toOption.get.values,
      NonEmptyList.of(
        new MediaType("application", "jsonpath"),
        MediaType.application.sql.withExtensions(Map("charset" -> "UTF-8")),
      ),
    )
    assertEquals(
      parsedMixed.toOption.get.renderString,
      "Accept-Query: application/jsonpath, application/sql; charset=\"UTF-8\"",
    )

    // Quoted string with parameters
    val quotedWithParams = "\"application/sql\";charset=\"UTF-8\""
    val parsedQuotedWithParams = `Accept-Query`.parse(quotedWithParams)
    assert(parsedQuotedWithParams.isRight)
    assertEquals(
      parsedQuotedWithParams.toOption.get.values,
      NonEmptyList.of(MediaType.application.sql.withExtensions(Map("charset" -> "UTF-8"))),
    )
  }
}
