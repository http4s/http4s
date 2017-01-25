package org.http4s.headers

import java.time.{Instant, ZoneId, ZonedDateTime}

class ExpiresSpec extends HeaderLaws {
  checkAll("Expires", headerLaws(Expires))

  val gmtDate = ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneId.of("GMT"))
  val epoch = Instant.ofEpochMilli(0)
  val epochString = "Expires: Thu, 01 Jan 1970 00:00:00 GMT"

  "render" should {
    "format GMT date according to RFC 1123" in {
      Expires(Instant.from(gmtDate)).renderString must_== "Expires: Sun, 06 Nov 1994 08:49:37 GMT"
    }
  }

  "parse" should {
    "accept format RFC 1123" in {
      Expires.parse("Sun, 06 Nov 1994 08:49:37 GMT").map(_.expirationDate) must beRight(Instant.from(gmtDate))
    }
    "accept 0 value (This value is not legal but it used by some servers)" in {
      // 0 is an illegal value used to denote an expired header, should be
      // equivalent to expiration set at the epoch
      Expires.parse("0").map(_.expirationDate) must beRight(epoch)
      Expires.parse("0").map(_.renderString) must beRight(epochString)
    }
    "accept -1 value (This value is not legal but it used by some servers)" in {
      // 0 is an illegal value used to denote an expired header, should be
      // equivalent to expiration set at the epoch
      Expires.parse("-1").map(_.expirationDate) must beRight(epoch)
      Expires.parse("-1").map(_.renderString) must beRight(epochString)
    }
  }
}
