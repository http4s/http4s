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

class IfModifiedSinceSuite extends Http4sSuite {
  private val rfc7232Example = HttpDate.unsafeFromZonedDateTime(
    ZonedDateTime.of(1994, Month.OCTOBER.getValue, 29, 19, 43, 31, 0, ZoneOffset.UTC)
  )

  test("parse If-Modified-Since") {
    assertEquals(
      `If-Modified-Since`.parse("Sat, 29 Oct 1994 19:43:31 GMT"),
      Right(`If-Modified-Since`(rfc7232Example)),
    )
  }
  test("fail to parse invalid If-Modified-Since") {
    assert(`If-Modified-Since`.parse("foo").isLeft)
  }
}
