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

import java.time.{ZoneId, ZonedDateTime}

class DateSpec extends HeaderLaws {
  checkAll("Date", headerLaws(Date))

  val gmtDate = ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneId.of("GMT"))

  "render" should {
    "format GMT date according to RFC 1123" in {
      Date(
        HttpDate.unsafeFromZonedDateTime(
          gmtDate)).renderString must_== "Date: Sun, 06 Nov 1994 08:49:37 GMT"
    }
    "format UTC date according to RFC 1123" in {
      val utcDate = ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneId.of("UTC"))
      Date(
        HttpDate.unsafeFromZonedDateTime(
          utcDate)).renderString must_== "Date: Sun, 06 Nov 1994 08:49:37 GMT"
    }
  }

  "fromDate" should {
    "accept format RFC 1123" in {
      Date.parse("Sun, 06 Nov 1994 08:49:37 GMT").map(_.date) must beRight(
        HttpDate.unsafeFromZonedDateTime(gmtDate))
    }
    "accept format RFC 1036" in {
      Date.parse("Sunday, 06-Nov-94 08:49:37 GMT").map(_.date) must beRight(
        HttpDate.unsafeFromZonedDateTime(gmtDate))
    }
    "accept format ANSI date" in {
      Date.parse("Sun Nov  6 08:49:37 1994").map(_.date) must beRight(
        HttpDate.unsafeFromZonedDateTime(gmtDate))
      Date.parse("Sun Nov 16 08:49:37 1994").map(_.date) must beRight(
        HttpDate.unsafeFromZonedDateTime(gmtDate.plusDays(10)))
    }
  }
}
