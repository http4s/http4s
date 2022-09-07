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

class AcceptEncodingSuite extends HeaderLaws {
  checkAll("Accept-Encoding", headerLaws[`Accept-Encoding`])

  test("is satisfied by a content coding if the q value is > 0") {
    forAll { (h: `Accept-Encoding`, cc: ContentCoding) =>
      h.qValue(cc) > QValue.Zero ==> h.satisfiedBy(cc)
    }
  }

  test("is not satisfied by a content coding if the q value is 0") {
    forAll { (h: `Accept-Encoding`, cc: ContentCoding) =>
      !`Accept-Encoding`(h.values.map(_.withQValue(QValue.Zero))).satisfiedBy(cc)
    }
  }

  test("matches atom before splatted") {
    val acceptEncoding =
      `Accept-Encoding`(ContentCoding.*, ContentCoding.gzip.withQValue(qValue"0.5"))
    assertEquals(acceptEncoding.qValue(ContentCoding.gzip), qValue"0.5")
  }

  test("matches splatted if atom not present") {
    val acceptEncoding =
      `Accept-Encoding`(ContentCoding.*, ContentCoding.compress.withQValue(qValue"0.5"))
    assertEquals(acceptEncoding.qValue(ContentCoding.gzip), QValue.One)
  }

  test("rejects content coding matching atom with q=0") {
    val acceptEncoding =
      `Accept-Encoding`(ContentCoding.*, ContentCoding.gzip.withQValue(QValue.Zero))
    assertEquals(acceptEncoding.qValue(ContentCoding.gzip), QValue.Zero)
  }

  test("rejects content coding matching splat with q=0") {
    val acceptEncoding = `Accept-Encoding`(
      ContentCoding.*.withQValue(QValue.Zero),
      ContentCoding.compress.withQValue(qValue"0.5"),
    )
    assertEquals(acceptEncoding.qValue(ContentCoding.gzip), QValue.Zero)
  }

  test("rejects unmatched content coding") {
    val acceptEncoding = `Accept-Encoding`(ContentCoding.compress.withQValue(qValue"0.5"))
    assertEquals(acceptEncoding.qValue(ContentCoding.gzip), QValue.Zero)
  }
}
