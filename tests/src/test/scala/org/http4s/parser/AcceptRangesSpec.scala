/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
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
