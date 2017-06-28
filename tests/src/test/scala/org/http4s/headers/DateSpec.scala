package org.http4s
package headers

import cats.implicits._
import java.time.{Instant, ZoneId, ZonedDateTime}

class DateSpec extends HeaderLaws {
  checkAll("Date", headerLaws(Date))

  val gmtDate = ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneId.of("GMT"))

  "render" should {
    "format GMT date according to RFC 1123" in {
      Date(Instant.from(gmtDate)).renderString must_== "Date: Sun, 06 Nov 1994 08:49:37 GMT"
    }
    "format UTC date according to RFC 1123" in {
      val utcDate = ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneId.of("UTC"))
      Date(Instant.from(utcDate)).renderString must_== "Date: Sun, 06 Nov 1994 08:49:37 GMT"
    }
  }

  "fromDate" should {
    "accept format RFC 1123" in {
      Date.parse("Sun, 06 Nov 1994 08:49:37 GMT").map(_.date) must beRight(Instant.from(gmtDate))
    }
    "accept format RFC 1036" in {
      Date.parse("Sunday, 06-Nov-94 08:49:37 GMT").map(_.date) must beRight(Instant.from(gmtDate))
    }
    "accept format ANSI date" in {
      Date.parse("Sun Nov  6 08:49:37 1994").map(_.date) must beRight(Instant.from(gmtDate))
      Date.parse("Sun Nov 16 08:49:37 1994").map(_.date) must beRight(Instant.from(gmtDate.plusDays(10)))
    }
  }
}
