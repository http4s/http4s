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
