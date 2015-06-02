package org.http4s
package headers

import org.http4s.headers.Range.SubRange

class ContentRangeParserSpec extends HeaderParserSpec(`Content-Range`) {
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
      hparse(header.value) must_== Some(header)
    }
  }
}
