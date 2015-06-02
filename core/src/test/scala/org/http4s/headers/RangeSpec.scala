package org.http4s
package headers

import org.http4s.headers.Range.SubRange


class RangeSpec extends HeaderParserSpec(Range) {

  "Range Header" should {
    "parse Range" in {
      val headers = Seq(
        Range(RangeUnit.Bytes, SubRange(0, 500)),
        Range(RangeUnit.Bytes, SubRange(0, 499), SubRange(500, 999), SubRange(1000, 1500)),
        Range(RangeUnit("page"), SubRange(0, 100)),
        Range(10),
        Range(-90)
      )

      forall(headers) { header =>
        hparse(header.value) must_== Some(header)
      }
    }
  }
}


