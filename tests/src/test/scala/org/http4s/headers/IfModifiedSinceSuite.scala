/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import java.time.{Month, ZoneOffset, ZonedDateTime}

class IfModifiedSinceSuite extends Http4sSuite {
  val rfc7232Example = HttpDate.unsafeFromZonedDateTime(
    ZonedDateTime.of(1994, Month.OCTOBER.getValue, 29, 19, 43, 31, 0, ZoneOffset.UTC))

  test("parse If-Modified-Since") {
    assertEquals(
      `If-Modified-Since`.parse("Sat, 29 Oct 1994 19:43:31 GMT"),
      Right(`If-Modified-Since`(rfc7232Example)))
  }
}
