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

import org.http4s.RangeUnit
import org.http4s.headers.`Accept-Ranges`
import org.http4s.syntax.header._

class AcceptRangesSpec extends Http4sSuite {
  def parse(value: String): ParseResult[`Accept-Ranges`] = `Accept-Ranges`.parse(value)

  val ranges = List(
    `Accept-Ranges`.bytes,
    `Accept-Ranges`.none,
    `Accept-Ranges`(RangeUnit("foo")),
    `Accept-Ranges`(RangeUnit.Bytes, RangeUnit("bar")),
  )

  test("Accept-Ranges header should Give correct header value") {
    assertEquals(ranges.map(_.value), List("bytes", "none", "foo", "bytes, bar"))
  }

//    test("Accept-Ranges header should Do whitespace right") {
//      val value = " bytes"
//      assertEquals(parse(value), `Accept-Ranges`.bytes)
//    }

  test("Accept-Ranges header should Parse correctly") {
    ranges.foreach { r =>
      assertEquals(parse(r.value), Right(r))
    }
  }

}
