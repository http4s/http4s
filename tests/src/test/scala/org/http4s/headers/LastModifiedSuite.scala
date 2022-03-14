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

import java.time.Month
import java.time.ZoneOffset
import java.time.ZonedDateTime

class LastModifiedSuite extends Http4sSuite {
  private val rfc7232Example = HttpDate.unsafeFromZonedDateTime(
    ZonedDateTime.of(1994, Month.NOVEMBER.getValue, 15, 12, 45, 26, 0, ZoneOffset.UTC)
  )

  test("parse Last-Modified") {
    assertEquals(
      Header[`Last-Modified`].parse("Tue, 15 Nov 1994 12:45:26 GMT"),
      Right(`Last-Modified`(rfc7232Example)),
    )
  }
}
