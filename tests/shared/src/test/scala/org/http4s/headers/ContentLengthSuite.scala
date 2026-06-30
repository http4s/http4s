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

import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.header._
import org.scalacheck.Prop._

class ContentLengthSuite extends HeaderLaws {
  checkAll("Content-Length", headerLaws[`Content-Length`])

  test("fromLong should reject negative lengths") {
    forAll { (length: Long) =>
      length < 0 ==>
        `Content-Length`.fromLong(length).isLeft
    }
  }
  test("fromLong should accept non-negative lengths") {
    forAll { (length: Long) =>
      length >= 0 ==> {
        `Content-Length`.fromLong(length).map(_.length) == Right(length)
      }
    }
  }

  test("fromString should reject negative lengths") {
    forAll { (length: Long) =>
      length < 0 ==>
        `Content-Length`.parse(length.toString).isLeft
    }
  }

  test("fromString should reject non-numeric strings") {
    forAll { (s: String) =>
      !s.matches("[0-9]+") ==>
        `Content-Length`.parse(s).isLeft
    }
  }

  test("fromString should be consistent with apply") {
    forAll { (length: Long) =>
      length >= 0 ==> {
        `Content-Length`.parse(length.toString) == `Content-Length`.fromLong(length)
      }
    }
  }
  test("fromString should roundtrip") {
    forAll { (l: Long) =>
      (l >= 0) ==> {
        `Content-Length`
          .fromLong(l)
          .map(_.value)
          .flatMap(`Content-Length`.parse) ==
          `Content-Length`.fromLong(l)
      }
    }
  }

  test("modify should update the length if positive") {
    forAll { (length: Long) =>
      length >= 0 ==> {
        `Content-Length`.zero.modify(_ + length) == `Content-Length`.fromLong(length).toOption
      }
    }
  }
  test("modify should fail to update if the result is negative") {
    forAll { (length: Long) =>
      length > 0 ==>
        `Content-Length`.zero.modify(_ - length).isEmpty
    }
  }
}
