package org.http4s.parser

import org.http4s.Header.`Accept-Ranges`
import org.specs2.mutable.Specification
import org.http4s.RangeUnit

class AcceptRangesSpec extends Specification with HeaderParserHelper[`Accept-Ranges`] {

  def hparse(value: String) = HttpParser.ACCEPT_RANGES(value)

  "Accept-Ranges header" should {

    val ranges = List(`Accept-Ranges`.bytes,
                      `Accept-Ranges`.none,
                      `Accept-Ranges`(RangeUnit.CustomRangeUnit("foo")),
                      `Accept-Ranges`(RangeUnit.bytes, RangeUnit.CustomRangeUnit("bar")))

    "Give correct header value" in {
      ranges.map(_.value) must be_== (List("bytes", "none", "foo", "bytes, bar"))
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
