/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import java.time.{Month, ZoneOffset, ZonedDateTime}

class LastModifiedSuite extends Http4sSuite {
  val rfc7232Example = HttpDate.unsafeFromZonedDateTime(
    ZonedDateTime.of(1994, Month.NOVEMBER.getValue, 15, 12, 45, 26, 0, ZoneOffset.UTC))

  test("parse Last-Modified") {
    assertEquals(
      `Last-Modified`.parse("Tue, 15 Nov 1994 12:45:26 GMT"),
      Right(`Last-Modified`(rfc7232Example)))
  }
}
