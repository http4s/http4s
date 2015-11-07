package org.http4s.parser

import org.http4s.headers.`Accept-Charset`
import org.http4s._
import scalaz.Validation

class AcceptCharsetSpec extends Http4sSpec with HeaderParserHelper[`Accept-Charset`] {

  def hparse(value: String): ParseResult[`Accept-Charset`] = HttpHeaderParser.ACCEPT_CHARSET(value)

  "Accept-Charset" should {
    "parse any list of CharsetRanges to itself" in {
      prop { h: `Accept-Charset` =>
        hparse(h.value) must be_\/-(h)
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
