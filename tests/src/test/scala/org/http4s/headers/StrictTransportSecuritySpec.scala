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

package org.http4s.headers

import org.http4s.ParseFailure
import scala.concurrent.duration._

class StrictTransportSecuritySpec extends HeaderLaws {
  checkAll("StrictTransportSecurity", headerLaws(`Strict-Transport-Security`))

  "fromLong" should {
    "support positive max age in seconds" in {
      `Strict-Transport-Security`.fromLong(365).map(_.renderString) must beRight(
        "Strict-Transport-Security: max-age=365; includeSubDomains")
      `Strict-Transport-Security`
        .fromLong(365, includeSubDomains = false)
        .map(_.renderString) must beRight("Strict-Transport-Security: max-age=365")
      `Strict-Transport-Security`
        .fromLong(365, preload = true)
        .map(_.renderString) must beRight(
        "Strict-Transport-Security: max-age=365; includeSubDomains; preload")
    }
    "reject negative max age in seconds" in {
      `Strict-Transport-Security`.fromLong(-365) must beLeft
    }
  }

  "unsafeFromDuration" should {
    "build for valid durations" in {
      `Strict-Transport-Security`
        .unsafeFromDuration(10.hours)
        .renderString must_== "Strict-Transport-Security: max-age=36000; includeSubDomains"
      `Strict-Transport-Security`
        .unsafeFromDuration(10.hours, includeSubDomains = false)
        .renderString must_== "Strict-Transport-Security: max-age=36000"
      `Strict-Transport-Security`
        .unsafeFromDuration(10.hours, preload = true)
        .renderString must_== "Strict-Transport-Security: max-age=36000; includeSubDomains; preload"
    }
    "fail for negative durations" in {
      `Strict-Transport-Security`.unsafeFromDuration(-10.hours).value must throwA[ParseFailure]
    }
  }

  "unsafeFromLong" should {
    "build for valid durations" in {
      `Strict-Transport-Security`
        .unsafeFromLong(10)
        .renderString must_== "Strict-Transport-Security: max-age=10; includeSubDomains"
      `Strict-Transport-Security`
        .unsafeFromLong(10, includeSubDomains = false)
        .renderString must_== "Strict-Transport-Security: max-age=10"
      `Strict-Transport-Security`
        .unsafeFromLong(10, preload = true)
        .renderString must_== "Strict-Transport-Security: max-age=10; includeSubDomains; preload"
    }
    "fail for negative durations" in {
      `Strict-Transport-Security`.unsafeFromLong(-10).value must throwA[ParseFailure]
    }
  }

  "render" should {
    "include max age in seconds" in {
      `Strict-Transport-Security`
        .unsafeFromDuration(365.days)
        .renderString must_== "Strict-Transport-Security: max-age=31536000; includeSubDomains"
    }
    "allow no sub domains" in {
      `Strict-Transport-Security`
        .unsafeFromDuration(365.days, includeSubDomains = false)
        .renderString must_== "Strict-Transport-Security: max-age=31536000"
    }
    "support preload" in {
      `Strict-Transport-Security`
        .unsafeFromDuration(365.days, preload = true)
        .renderString must_== "Strict-Transport-Security: max-age=31536000; includeSubDomains; preload"
    }
  }

  "parse" should {
    "accept age" in {
      `Strict-Transport-Security`.parse("max-age=31536000") must beRight(
        `Strict-Transport-Security`.unsafeFromDuration(365.days, false))
    }
    "accept age and subdomains" in {
      `Strict-Transport-Security`.parse("max-age=31536000; includeSubDomains") must beRight(
        `Strict-Transport-Security`.unsafeFromDuration(365.days, true))
    }
    "accept age, subdomains and preload" in {
      `Strict-Transport-Security`.parse(
        "max-age=31536000; includeSubDomains; preload") must beRight(
        `Strict-Transport-Security`.unsafeFromDuration(365.days, true, true))
    }
  }
}
