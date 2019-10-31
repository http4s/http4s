package org.http4s
package headers

import java.time.{ZoneId, ZonedDateTime}

class ExpiresSpec extends HeaderLaws {
  checkAll("Expires", headerLaws(Expires))

  val gmtDate = ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneId.of("GMT"))
  val epochString = "Expires: Thu, 01 Jan 1970 00:00:00 GMT"

  "render" should {
    "format GMT date according to RFC 1123" in {
      Expires(HttpDate.unsafeFromZonedDateTime(gmtDate)).renderString must_== "Expires: Sun, 06 Nov 1994 08:49:37 GMT"
    }
  }

  "parse" should {
    "accept format RFC 1123" in {
      Expires.parse("Sun, 06 Nov 1994 08:49:37 GMT").right.map(_.expirationDate) must beRight(
        HttpDate.unsafeFromZonedDateTime(gmtDate))
    }
    "accept 0 value (This value is not legal but it used by some servers)" in {
      // 0 is an illegal value used to denote an expired header, should be
      // equivalent to expiration set at the epoch
      Expires.parse("0").right.map(_.expirationDate) must beRight(HttpDate.Epoch)
      Expires.parse("0").right.map(_.renderString) must beRight(epochString)
    }
    "accept -1 value (This value is not legal but it used by some servers)" in {
      // 0 is an illegal value used to denote an expired header, should be
      // equivalent to expiration set at the epoch
      Expires.parse("-1").right.map(_.expirationDate) must beRight(HttpDate.Epoch)
      Expires.parse("-1").right.map(_.renderString) must beRight(epochString)
    }
  }
}
