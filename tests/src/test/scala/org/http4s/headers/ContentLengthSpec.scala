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

class ContentLengthSpec extends HeaderLaws {
  checkAll("Content-Length", headerLaws(`Content-Length`))

  "fromLong" should {
    "reject negative lengths" in prop { (length: Long) =>
      length < 0 ==> {
        `Content-Length`.fromLong(length) must beLeft
      }
    }
    "accept non-negative lengths" in prop { (length: Long) =>
      length >= 0 ==> {
        `Content-Length`.fromLong(length).map(_.length) must beRight(length)
      }
    }
  }

  "fromString" should {
    "reject negative lengths" in prop { (length: Long) =>
      length < 0 ==> {
        `Content-Length`.parse(length.toString) must beLeft
      }
    }

    "reject non-numeric strings" in prop { (s: String) =>
      !s.matches("[0-9]+") ==> {
        `Content-Length`.parse(s) must beLeft
      }
    }

    "be consistent with apply" in prop { (length: Long) =>
      length >= 0 ==> {
        `Content-Length`.parse(length.toString) must_== `Content-Length`.fromLong(length)
      }
    }
    "roundtrip" in prop { (l: Long) =>
      (l >= 0) ==> {
        `Content-Length`
          .fromLong(l)
          .map(_.value)
          .flatMap(`Content-Length`.parse) must_== `Content-Length`.fromLong(l)
      }
    }
  }

  "modify" should {
    "update the length if positive" in prop { (length: Long) =>
      length >= 0 ==> {
        `Content-Length`.zero.modify(_ + length) must_== `Content-Length`
          .fromLong(length)
          .toOption
      }
    }
    "fail to update if the result is negative" in prop { (length: Long) =>
      length > 0 ==> {
        `Content-Length`.zero.modify(_ - length) must beNone
      }
    }
  }
}
