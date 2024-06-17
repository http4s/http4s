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
import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.header._

import scala.concurrent.duration._

class StrictTransportSecuritySuite extends HeaderLaws {
  checkAll("StrictTransportSecurity", headerLaws[`Strict-Transport-Security`])

  test("fromLong should support positive max age in seconds") {
    assertEquals(
      `Strict-Transport-Security`.fromLong(365).map(_.renderString),
      Right("Strict-Transport-Security: max-age=365; includeSubDomains"),
    )
    assertEquals(
      `Strict-Transport-Security`
        .fromLong(365, includeSubDomains = false)
        .map(_.renderString),
      Right("Strict-Transport-Security: max-age=365"),
    )
    assertEquals(
      `Strict-Transport-Security`
        .fromLong(365, preload = true)
        .map(_.renderString),
      Right("Strict-Transport-Security: max-age=365; includeSubDomains; preload"),
    )
  }
  test("fromLong should reject negative max age in seconds") {
    assert(`Strict-Transport-Security`.fromLong(-365).isLeft)
  }

  test("unsafeFromDuration should build for valid durations") {
    assertEquals(
      `Strict-Transport-Security`
        .unsafeFromDuration(10.hours)
        .renderString,
      "Strict-Transport-Security: max-age=36000; includeSubDomains",
    )
    assertEquals(
      `Strict-Transport-Security`
        .unsafeFromDuration(10.hours, includeSubDomains = false)
        .renderString,
      "Strict-Transport-Security: max-age=36000",
    )
    assertEquals(
      `Strict-Transport-Security`
        .unsafeFromDuration(10.hours, preload = true)
        .renderString,
      "Strict-Transport-Security: max-age=36000; includeSubDomains; preload",
    )
  }
  test("unsafeFromDuration should fail for negative durations") {
    intercept[ParseFailure] {
      `Strict-Transport-Security`.unsafeFromDuration(-10.hours).value
    }
  }

  test("unsafeFromLong should build for valid durations") {
    assertEquals(
      `Strict-Transport-Security`
        .unsafeFromLong(10)
        .renderString,
      "Strict-Transport-Security: max-age=10; includeSubDomains",
    )
    assertEquals(
      `Strict-Transport-Security`
        .unsafeFromLong(10, includeSubDomains = false)
        .renderString,
      "Strict-Transport-Security: max-age=10",
    )
    assertEquals(
      `Strict-Transport-Security`
        .unsafeFromLong(10, preload = true)
        .renderString,
      "Strict-Transport-Security: max-age=10; includeSubDomains; preload",
    )
  }
  test("unsafeFromLong should fail for negative durations") {
    intercept[ParseFailure] {
      `Strict-Transport-Security`.unsafeFromLong(-10).value
    }
  }

  test("render should include max age in seconds") {
    assertEquals(
      `Strict-Transport-Security`
        .unsafeFromDuration(365.days)
        .renderString,
      "Strict-Transport-Security: max-age=31536000; includeSubDomains",
    )
  }
  test("render should allow no sub domains") {
    assertEquals(
      `Strict-Transport-Security`
        .unsafeFromDuration(365.days, includeSubDomains = false)
        .renderString,
      "Strict-Transport-Security: max-age=31536000",
    )
  }
  test("render should support preload") {
    assertEquals(
      `Strict-Transport-Security`
        .unsafeFromDuration(365.days, preload = true)
        .renderString,
      "Strict-Transport-Security: max-age=31536000; includeSubDomains; preload",
    )
  }

  test("parse should accept age") {
    assertEquals(
      `Strict-Transport-Security`.parse("max-age=31536000"),
      Right(`Strict-Transport-Security`.unsafeFromDuration(365.days, includeSubDomains = false)),
    )
  }
  test("parse should accept age and subdomains") {
    assertEquals(
      `Strict-Transport-Security`.parse("max-age=31536000; includeSubDomains"),
      Right(`Strict-Transport-Security`.unsafeFromDuration(365.days, includeSubDomains = true)),
    )
  }
  test("parse should accept age, subdomains and preload") {
    assertEquals(
      `Strict-Transport-Security`.parse("max-age=31536000; includeSubDomains; preload"),
      Right(
        `Strict-Transport-Security`.unsafeFromDuration(
          365.days,
          includeSubDomains = true,
          preload = true,
        )
      ),
    )
  }
}
