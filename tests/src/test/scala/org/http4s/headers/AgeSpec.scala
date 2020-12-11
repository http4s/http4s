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

import org.http4s.{ParseFailure, ParseResult}
import org.scalacheck.Prop.forAll
import scala.concurrent.duration._

class AgeSpec extends HeaderLaws {
  checkAll("Age", headerLaws(Age))

  "render" should {
    "age in seconds" in {
      Age.fromLong(120).map(_.renderString) must_== ParseResult.success("Age: 120")
    }
  }

  "build" should {
    "build correctly for positives" in {
      Age.fromLong(0).map(_.value) must beLike { case Right("0") => ok }
    }
    "fail for negatives" in {
      Age.fromLong(-10).map(_.value) must beLeft
    }
    "build unsafe for positives" in {
      Age.unsafeFromDuration(0.seconds).value must_== "0"
      Age.unsafeFromLong(10).value must_== "10"
    }
    "fail unsafe for negatives" in {
      Age.unsafeFromDuration(-10.seconds).value must throwA[ParseFailure]
      Age.unsafeFromLong(-10).value must throwA[ParseFailure]
    }
  }

  "produce duration" should {
    "safe" in {
      Age.unsafeFromLong(10).duration must_== Option(10.seconds)
    }
    "unsafe" in {
      Age.unsafeFromLong(10).unsafeDuration must_== 10.seconds
    }
  }

  "parse" should {
    "accept duration on seconds" in {
      Age.parse("120").map(_.age) must beRight(120L)
    }
    "reject negative values" in {
      Age.parse("-120").map(_.age) must beLeft
    }
    "roundtrip" in {
      forAll { (l: Long) =>
        (l >= 0) ==> {
          Age.fromLong(l).map(_.value).flatMap(Age.parse) must_== Age.fromLong(l)
        }
      }
    }
  }
}
