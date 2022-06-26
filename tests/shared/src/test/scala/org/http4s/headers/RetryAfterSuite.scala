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
import scala.concurrent.duration._

class RetryAfterSuite extends HeaderLaws {
  checkAll("Retry-After", headerLaws[`Retry-After`])

  val gmtDate: ZonedDateTime = ZonedDateTime.of(1999, 12, 31, 23, 59, 59, 0, ZoneId.of("GMT"))

  test("render should format GMT date according to RFC 1123") {
    assertEquals(
      `Retry-After`(HttpDate.unsafeFromZonedDateTime(gmtDate)).renderString,
      "Retry-After: Fri, 31 Dec 1999 23:59:59 GMT",
    )
  }
  test("render should duration in seconds") {
    assertEquals(`Retry-After`.unsafeFromDuration(120.seconds).renderString, "Retry-After: 120")
  }

  test("build should build correctly for positives") {
    val Right(result) = `Retry-After`.fromLong(0).map(_.value)
    assertEquals(result, "0")
  }
  test("build should fail for negatives") {
    val Left(_) = `Retry-After`.fromLong(-10).map(_.value)
  }
  test("build should build unsafe for positives") {
    assertEquals(`Retry-After`.unsafeFromDuration(0.seconds).value, "0")
    assertEquals(`Retry-After`.unsafeFromLong(10).value, "10")
  }
  test("build should fail unsafe for negatives") {
    intercept[ParseFailure](`Retry-After`.unsafeFromDuration(-10.seconds).value)
    intercept[ParseFailure](`Retry-After`.unsafeFromLong(-10).value)
  }

  test("parse should accept http date") {
    assertEquals(
      `Retry-After`.parse("Fri, 31 Dec 1999 23:59:59 GMT").map(_.retry),
      Right(Left(HttpDate.unsafeFromZonedDateTime(gmtDate))),
    )
  }
  test("parse should accept duration on seconds") {
    assertEquals(`Retry-After`.parse("120").map(_.retry), Right(Right(120L)))
  }
}
