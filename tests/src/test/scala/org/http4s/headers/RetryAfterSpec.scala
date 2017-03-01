package org.http4s.headers

import java.time.{Instant, ZoneId, ZonedDateTime}

class RetryAfterSpec extends HeaderLaws {
  checkAll("Retry-After", headerLaws(`Retry-After`))

  val gmtDate: ZonedDateTime = ZonedDateTime.of(1999, 12, 31, 23, 59, 59, 0, ZoneId.of("GMT"))

  "render" should {
    "format GMT date according to RFC 1123" in {
      `Retry-After`(Left(Instant.from(gmtDate))).renderString must_== "Retry-After: Fri, 31 Dec 1999 23:59:59 GMT"
    }
    "duration in seconds" in {
      `Retry-After`(Right(120)).renderString must_== "Retry-After: 120"
    }
  }

  "parse" should {
    "accept http date" in {
      `Retry-After`.parse("Fri, 31 Dec 1999 23:59:59 GMT").map(_.retry) must be_\/-(Left(Instant.from(gmtDate)))
    }
    "accept duration on seconds" in {
      `Retry-After`.parse("120").map(_.retry) must be_\/-(Right(120))
    }
  }
}
