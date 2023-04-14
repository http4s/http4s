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

import cats.syntax.all._
import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.header._

import java.time.ZoneId
import java.time.ZonedDateTime

class DeprecationSuite extends HeaderLaws {
  checkAll("Deprecation", headerLaws[Deprecation])

  private val gmtDeprecation = ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneId.of("GMT"))

  test("render should format GMT date according to RFC 1123") {
    assertEquals(
      Deprecation(HttpDate.unsafeFromZonedDateTime(gmtDeprecation)).value,
      "Sun, 06 Nov 1994 08:49:37 GMT",
    )
  }
  test("render should format UTC date according to RFC 1123") {
    val utcDeprecation = ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneId.of("UTC"))
    assertEquals(
      Deprecation(HttpDate.unsafeFromZonedDateTime(utcDeprecation)).value,
      "Sun, 06 Nov 1994 08:49:37 GMT",
    )
  }

  test("fromDeprecation should accept format RFC 1123") {
    assertEquals(
      Deprecation.parse("Sun, 06 Nov 1994 08:49:37 GMT").map(_.date),
      Right(HttpDate.unsafeFromZonedDateTime(gmtDeprecation)),
    )
  }
  test("fromDeprecation should not accept format RFC 1036") {
    assertEquals(
      Deprecation.parse("Sunday, 06-Nov-94 08:49:37 GMT").leftMap(_.sanitized),
      Left("Invalid Deprecation header"),
    )
  }
  test("fromDeprecation should not accept format ANSI date") {
    assertEquals(
      Deprecation.parse("Sun Nov  6 08:49:37 1994").leftMap(_.sanitized),
      Left("Invalid Deprecation header"),
    )
  }
}
