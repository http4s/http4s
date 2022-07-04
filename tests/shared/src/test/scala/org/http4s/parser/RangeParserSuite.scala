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

import org.http4s.headers.Range
import org.http4s.headers.Range.SubRange
import org.http4s.headers.`Content-Range`
import org.http4s.syntax.header._

class RangeParserSuite extends Http4sSuite {
  test("RangeParser should parse Range") {
    val headers = List(
      Range(RangeUnit.Bytes, SubRange(0, 500)),
      Range(RangeUnit.Bytes, SubRange(0, 499), SubRange(500, 999), SubRange(1000, 1500)),
      Range(RangeUnit("page"), SubRange(0, 100)),
      Range(10),
      Range(-90),
    )

    headers.foreach { header =>
      assertEquals(Header[Range].parse(header.value), Right(header))
    }
  }

  test("RangeParser should parse Content-Range") {
    val headers = List(
      `Content-Range`(RangeUnit.Bytes, SubRange(10, 500), None),
      `Content-Range`(RangeUnit.Bytes, SubRange(0, 500), Some(500)),
      `Content-Range`(RangeUnit("page"), SubRange(0, 100), Some(100)),
      `Content-Range`(SubRange(10, 30)),
    )

    headers.foreach { header =>
      assertEquals(`Content-Range`.parse(header.value), Right(header))
    }
  }
}
