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
import org.http4s.syntax.header._

import java.time.ZoneId
import java.time.ZonedDateTime

class ExpiresSuite extends HeaderLaws {
  checkAll("Expires", headerLaws[Expires])

  private val gmtDate = ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneId.of("GMT"))
  private val epochString = "Thu, 01 Jan 1970 00:00:00 GMT"

  test("render should format GMT date according to RFC 1123") {
    assertEquals(
      Expires(HttpDate.unsafeFromZonedDateTime(gmtDate)).value,
      "Sun, 06 Nov 1994 08:49:37 GMT",
    )
  }

  test("parse should accept format RFC 1123") {
    assertEquals(
      Expires.parse("Sun, 06 Nov 1994 08:49:37 GMT").map(_.expirationDate),
      Right(HttpDate.unsafeFromZonedDateTime(gmtDate)),
    )
  }
  test("parse should accept 0 value (This value is not legal but it used by some servers)") {
    // 0 is an illegal value used to denote an expired header, should be
    // equivalent to expiration set at the epoch
    assertEquals(Expires.parse("0").map(_.expirationDate), Right(HttpDate.Epoch))
    assertEquals(Expires.parse("0").map(_.value), Right(epochString))
  }
  test("parse should accept -1 value (This value is not legal but it used by some servers)") {
    // 0 is an illegal value used to denote an expired header, should be
    // equivalent to expiration set at the epoch
    assertEquals(Expires.parse("-1").map(_.expirationDate), Right(HttpDate.Epoch))
    assertEquals(Expires.parse("-1").map(_.value), Right(epochString))
  }
}
