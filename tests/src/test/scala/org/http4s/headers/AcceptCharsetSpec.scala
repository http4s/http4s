package org.http4s
package headers

class AcceptCharsetSpec extends HeaderLaws {
  checkAll("Accept-Charset", headerLaws(`Accept-Charset`))

  "is satisfied by a charset if the q value is > 0" in {
    prop { (h: `Accept-Charset`, cs: Charset) =>
      h.qValue(cs) > QValue.Zero ==> { h.isSatisfiedBy(cs) }
    }
  }

  "is not satisfied by a charset if the q value is 0" in {
    prop { (h: `Accept-Charset`, cs: Charset) =>
      !h.map(_.withQValue(QValue.Zero)).isSatisfiedBy(cs)
    }
  }
}
