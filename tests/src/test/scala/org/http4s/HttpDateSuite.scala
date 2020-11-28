/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.effect.IO
import java.time.{Month, ZoneOffset, ZonedDateTime}

class HttpDateSuite extends Http4sSuite {
  test("current should be within a second of Instant.now") {
    for {
      current <- HttpDate.current[IO]
      now <- IO.delay(java.time.Instant.now).map(HttpDate.unsafeFromInstant)
      diff = current.epochSecond - now.epochSecond
    } yield assert(diff == 0 || diff == 1, "diff was " + diff)
  }

  val rfc7231Example = HttpDate.unsafeFromZonedDateTime(
    ZonedDateTime.of(1994, Month.NOVEMBER.getValue, 6, 8, 49, 37, 0, ZoneOffset.UTC))

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
