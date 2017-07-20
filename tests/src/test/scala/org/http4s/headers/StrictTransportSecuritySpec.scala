package org.http4s.headers

import cats.implicits._
import java.time.{Instant, ZoneId, ZonedDateTime}

import scala.concurrent.duration._

class StrictTransportSecuritySpec extends HeaderLaws {
  checkAll("StrictTransportSecurity", headerLaws(`Strict-Transport-Security`))

  "render" should {
    "include max age in seconds" in {
      `Strict-Transport-Security`(365.days).renderString must_== "Strict-Transport-Security: max-age=31536000; includeSubDomains"
    }
    "allow no sub domains" in {
      `Strict-Transport-Security`(365.days, includeSubDomains = false).renderString must_== "Strict-Transport-Security: max-age=31536000"
    }
    "support preload" in {
      `Strict-Transport-Security`(365.days, preload = true).renderString must_== "Strict-Transport-Security: max-age=31536000; includeSubDomains; preload"
    }
  }

  "parse" should {
    "accept age" in {
      `Strict-Transport-Security`.parse("max-age=31536000") must beRight(`Strict-Transport-Security`(365.days, false))
    }
    "accept age and subdomains" in {
      `Strict-Transport-Security`.parse("max-age=31536000; includeSubDomains") must beRight(`Strict-Transport-Security`(365.days, true))
    }
    "accept age, subdomains and preload" in {
      `Strict-Transport-Security`.parse("max-age=31536000; includeSubDomains; preload") must beRight(`Strict-Transport-Security`(365.days, true, true))
    }
  }
}
