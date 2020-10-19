/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

class AcceptEncodingSpec extends HeaderLaws {
  checkAll("Accept-Encoding", headerLaws(`Accept-Encoding`))

  "is satisfied by a content coding if the q value is > 0" in {
    prop { (h: `Accept-Encoding`, cc: ContentCoding) =>
      h.qValue(cc) > QValue.Zero ==> h.satisfiedBy(cc)
    }
  }

  "is not satisfied by a content coding if the q value is 0" in {
    prop { (h: `Accept-Encoding`, cc: ContentCoding) =>
      !(`Accept-Encoding`(h.values.map(_.withQValue(QValue.Zero))).satisfiedBy(cc))
    }
  }

  "matches atom before splatted" in {
    val acceptEncoding =
      `Accept-Encoding`(ContentCoding.*, ContentCoding.gzip.withQValue(QValue.q(0.5)))
    acceptEncoding.qValue(ContentCoding.gzip) must_== QValue.q(0.5)
  }

  "matches splatted if atom not present" in {
    val acceptEncoding =
      `Accept-Encoding`(ContentCoding.*, ContentCoding.compress.withQValue(QValue.q(0.5)))
    acceptEncoding.qValue(ContentCoding.gzip) must_== QValue.One
  }

  "rejects content coding matching atom with q=0" in {
    val acceptEncoding =
      `Accept-Encoding`(ContentCoding.*, ContentCoding.gzip.withQValue(QValue.Zero))
    acceptEncoding.qValue(ContentCoding.gzip) must_== QValue.Zero
  }

  "rejects content coding matching splat with q=0" in {
    val acceptEncoding = `Accept-Encoding`(
      ContentCoding.*.withQValue(QValue.Zero),
      ContentCoding.compress.withQValue(QValue.q(0.5)))
    acceptEncoding.qValue(ContentCoding.gzip) must_== QValue.Zero
  }

  "rejects unmatched content coding" in {
    val acceptEncoding = `Accept-Encoding`(ContentCoding.compress.withQValue(QValue.q(0.5)))
    acceptEncoding.qValue(ContentCoding.gzip) must_== QValue.Zero
  }
}
