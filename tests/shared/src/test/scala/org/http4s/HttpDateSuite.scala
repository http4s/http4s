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

import cats.effect.IO
import cats.kernel.laws.discipline.BoundedEnumerableTests
import cats.kernel.laws.discipline.HashTests
import cats.kernel.laws.discipline.OrderTests
import org.http4s.laws.discipline.arbitrary._

import java.time.Month
import java.time.ZoneOffset
import java.time.ZonedDateTime

class HttpDateSuite extends Http4sSuite {
  import HttpVersion._

  checkAll("HttpVersion", OrderTests[HttpVersion].order)

  checkAll("HttpVersion", HashTests[HttpVersion].hash)

  checkAll("HttpVersion", BoundedEnumerableTests[HttpVersion].boundedEnumerable)

  test("current should be within a second of Instant.now".flaky) {
    for {
      current <- HttpDate.current[IO]
      now <- IO(HttpDate.unsafeFromInstant(java.time.Instant.now))
      diff = now.epochSecond - current.epochSecond
    } yield assert(diff == 0 || diff == 1, "diff was " + diff)
  }

  private val rfc7231Example = HttpDate.unsafeFromZonedDateTime(
    ZonedDateTime.of(1994, Month.NOVEMBER.getValue, 6, 8, 49, 37, 0, ZoneOffset.UTC)
  )

  test("parse imf-fix-date") {
    assertEquals(HttpDate.fromString("Sun, 06 Nov 1994 08:49:37 GMT"), Right(rfc7231Example))
  }

  test("parse obsolete RFC 850 format") {
    assertEquals(HttpDate.fromString("Sunday, 06-Nov-94 08:49:37 GMT"), Right(rfc7231Example))
  }

  test("parse ANSI C's asctime() format") {
    assertEquals(HttpDate.fromString("Sun Nov  6 08:49:37 1994"), Right(rfc7231Example))
  }
}
