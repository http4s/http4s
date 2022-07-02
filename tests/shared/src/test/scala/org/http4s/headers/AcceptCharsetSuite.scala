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

import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.all._
import org.scalacheck.Prop._

class AcceptCharsetSuite extends HeaderLaws {
  checkAll("Accept-Charset", headerLaws[`Accept-Charset`])

  test("AcceptCharset is satisfied by a charset if the q value is > 0") {
    forAll { (h: `Accept-Charset`, cs: Charset) =>
      h.qValue(cs) > QValue.Zero ==> h.satisfiedBy(cs)
    }
  }

  test("AcceptCharset is not satisfied by a charset if the q value is 0") {
    forAll { (h: `Accept-Charset`, cs: Charset) =>
      !h.map(_.withQValue(QValue.Zero)).satisfiedBy(cs)
    }
  }

  test("AcceptCharset matches atom before splatted") {
    val acceptCharset =
      `Accept-Charset`(CharsetRange.*, CharsetRange.Atom(Charset.`UTF-8`, qValue"0.5"))
    assertEquals(acceptCharset.qValue(Charset.`UTF-8`), qValue"0.5")
  }

  test("AcceptCharset matches splatted if atom not present") {
    val acceptCharset =
      `Accept-Charset`(CharsetRange.*, CharsetRange.Atom(Charset.`ISO-8859-1`, qValue"0.5"))
    assertEquals(acceptCharset.qValue(Charset.`UTF-8`), QValue.One)
  }

  test("AcceptCharset rejects charset matching atom with q=0") {
    val acceptCharset =
      `Accept-Charset`(CharsetRange.*, CharsetRange.Atom(Charset.`UTF-8`, QValue.Zero))
    assertEquals(acceptCharset.qValue(Charset.`UTF-8`), QValue.Zero)
  }

  test("AcceptCharset rejects charset matching splat with q=0") {
    val acceptCharset = `Accept-Charset`(
      CharsetRange.*.withQValue(QValue.Zero),
      CharsetRange.Atom(Charset.`ISO-8859-1`, qValue"0.5"),
    )
    assertEquals(acceptCharset.qValue(Charset.`UTF-8`), QValue.Zero)
  }

  test("AcceptCharset rejects unmatched charset") {
    val acceptCharset = `Accept-Charset`(CharsetRange.Atom(Charset.`ISO-8859-1`, qValue"0.5"))
    assertEquals(acceptCharset.qValue(Charset.`UTF-8`), QValue.Zero)
  }
}
