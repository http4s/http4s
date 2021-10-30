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
import org.http4s.ParseResult
import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.header._
import org.scalacheck.Prop._

import scala.concurrent.duration._

class AgeSuite extends HeaderLaws {
  checkAll("Age", headerLaws[Age])

  test("render should age in seconds") {
    assertEquals(Age.fromLong(120).map(_.renderString), ParseResult.success("Age: 120"))
  }

  test("build should build correctly for positives") {
    assertEquals(Age.fromLong(0).map(_.value), Right("0"))
  }
  test("build should fail for negatives") {
    assert(Age.fromLong(-10).map(_.value).isLeft)
  }
  test("build should build unsafe for positives") {
    assertEquals(Age.unsafeFromDuration(0.seconds).value, "0")
    assertEquals(Age.unsafeFromLong(10).value, "10")
  }
  test("build should fail unsafe for negatives") {
    intercept[ParseFailure](Age.unsafeFromDuration(-10.seconds).value)
    intercept[ParseFailure](Age.unsafeFromLong(-10).value)
  }

  test("produce should safe") {
    assertEquals(Age.unsafeFromLong(10).duration, Option(10.seconds))
  }
  test("produce should unsafe") {
    assertEquals(Age.unsafeFromLong(10).unsafeDuration, 10.seconds)
  }

  test("parse should accept duration on seconds") {
    assertEquals(Age.parse("120").map(_.age), Right(120L))
  }
  test("parse should reject negative values") {
    assert(Age.parse("-120").map(_.age).isLeft)
  }
  test("parse should roundtrip") {
    forAll { (l: Long) =>
      (l >= 0) ==> {
        Age.fromLong(l).map(_.value).flatMap(Age.parse) == Age.fromLong(l)
      }
    }
  }
}
