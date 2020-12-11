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
package parser

import org.http4s.headers.{Range, `Content-Range`}
import org.http4s.headers.Range.SubRange

class RangeParserSpec extends Http4sSpec {
  "RangeParser" should {
    "parse Range" in {
      val headers = Seq(
        Range(RangeUnit.Bytes, SubRange(0, 500)),
        Range(RangeUnit.Bytes, SubRange(0, 499), SubRange(500, 999), SubRange(1000, 1500)),
        Range(RangeUnit("page"), SubRange(0, 100)),
        Range(10),
        Range(-90)
      )

      forall(headers) { header =>
        HttpHeaderParser.parseHeader(header.toRaw) must beRight(header)
      }
    }
  }

  "parse Content-Range" in {
    val headers = Seq(
      `Content-Range`(RangeUnit.Bytes, SubRange(10, None), None),
      `Content-Range`(RangeUnit.Bytes, SubRange(0, 500), Some(500)),
      `Content-Range`(RangeUnit("page"), SubRange(0, 100), Some(100)),
      `Content-Range`(10),
      `Content-Range`(-90),
      `Content-Range`(SubRange(10, 30))
    )

    forall(headers) { header =>
      HttpHeaderParser.parseHeader(header.toRaw) must beRight(header)
    }
  }
}
