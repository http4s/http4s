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
package headers

class AcceptCharsetSpec extends HeaderLaws {
  checkAll("Accept-Charset", headerLaws(`Accept-Charset`))

  "is satisfied by a charset if the q value is > 0" in {
    prop { (h: `Accept-Charset`, cs: Charset) =>
      h.qValue(cs) > QValue.Zero ==> h.satisfiedBy(cs)
    }
  }

  "is not satisfied by a charset if the q value is 0" in {
    prop { (h: `Accept-Charset`, cs: Charset) =>
      !(h.map(_.withQValue(QValue.Zero)).satisfiedBy(cs))
    }
  }

  "matches atom before splatted" in {
    val acceptCharset =
      `Accept-Charset`(CharsetRange.*, CharsetRange.Atom(Charset.`UTF-8`, QValue.q(0.5)))
    acceptCharset.qValue(Charset.`UTF-8`) must_== QValue.q(0.5)
  }

  "matches splatted if atom not present" in {
    val acceptCharset =
      `Accept-Charset`(CharsetRange.*, CharsetRange.Atom(Charset.`ISO-8859-1`, QValue.q(0.5)))
    acceptCharset.qValue(Charset.`UTF-8`) must_== QValue.One
  }

  "rejects charset matching atom with q=0" in {
    val acceptCharset =
      `Accept-Charset`(CharsetRange.*, CharsetRange.Atom(Charset.`UTF-8`, QValue.Zero))
    acceptCharset.qValue(Charset.`UTF-8`) must_== QValue.Zero
  }

  "rejects charset matching splat with q=0" in {
    val acceptCharset = `Accept-Charset`(
      CharsetRange.*.withQValue(QValue.Zero),
      CharsetRange.Atom(Charset.`ISO-8859-1`, QValue.q(0.5)))
    acceptCharset.qValue(Charset.`UTF-8`) must_== QValue.Zero
  }

  "rejects unmatched charset" in {
    val acceptCharset = `Accept-Charset`(CharsetRange.Atom(Charset.`ISO-8859-1`, QValue.q(0.5)))
    acceptCharset.qValue(Charset.`UTF-8`) must_== QValue.Zero
  }
}
