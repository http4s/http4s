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

package org.http4s.parser

import org.http4s.RangeUnit
import org.http4s.headers.`Accept-Ranges`
import org.specs2.mutable.Specification

class AcceptRangesSpec extends Specification with HeaderParserHelper[`Accept-Ranges`] {
  def hparse(value: String) = HttpHeaderParser.ACCEPT_RANGES(value)

  "Accept-Ranges header" should {
    val ranges = List(
      `Accept-Ranges`.bytes,
      `Accept-Ranges`.none,
      `Accept-Ranges`(RangeUnit("foo")),
      `Accept-Ranges`(RangeUnit.Bytes, RangeUnit("bar")))

    "Give correct header value" in {
      ranges.map(_.value) must be_==(List("bytes", "none", "foo", "bytes, bar"))
    }

//    "Do whitespace right" in {
//      val value = " bytes"
//      parse(value) must be_==(`Accept-Ranges`.bytes)
//    }

    "Parse correctly" in {
      foreach(ranges) { r =>
        parse(r.value) must be_==(r)
      }
    }
  }
}
