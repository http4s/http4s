package org.http4s.headers

import org.http4s._

class AcceptCharsetSpec extends HeaderParserSpec(`Accept-Charset`) {

  "Accept-Charset" should {
    "parse any list of CharsetRanges to itself" in {
      prop { h: `Accept-Charset` =>
        hparse(h.value) must_== Some(h)
      }
    }

    "is satisfied by a charset if the q value is > 0" in {
      prop { (h: `Accept-Charset`, cs: Charset) =>
        h.qValue(cs) > QValue.Zero ==> { h isSatisfiedBy cs }
      }
    }

    "is not satisfied by a charset if the q value is 0" in {
      prop { (h: `Accept-Charset`, cs: Charset) => !(h.map(_.withQValue(QValue.Zero)) isSatisfiedBy cs) }
    }
  }
}
