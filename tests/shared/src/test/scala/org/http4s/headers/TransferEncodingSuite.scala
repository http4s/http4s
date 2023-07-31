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

import cats.data.NonEmptyList
import cats.syntax.all._
import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.header._
import org.scalacheck.Prop.forAll

class TransferEncodingSuite extends HeaderLaws {
  checkAll("TransferEncoding", headerLaws[`Transfer-Encoding`])

  test("render should include all the encodings") {
    assertEquals(
      `Transfer-Encoding`(TransferCoding.chunked).renderString,
      "Transfer-Encoding: chunked",
    )
    assertEquals(
      `Transfer-Encoding`(TransferCoding.chunked, TransferCoding.gzip).renderString,
      "Transfer-Encoding: chunked, gzip",
    )
  }

  test("parse should accept single codings") {
    assertEquals(
      `Transfer-Encoding`.parse("chunked").map(_.values),
      Right(NonEmptyList.one(TransferCoding.chunked)),
    )
  }
  test("parse should accept multiple codings") {
    assertEquals(
      `Transfer-Encoding`.parse("chunked, gzip").map(_.values),
      Right(NonEmptyList.of(TransferCoding.chunked, TransferCoding.gzip)),
    )
    assertEquals(
      `Transfer-Encoding`.parse("chunked,gzip").map(_.values),
      Right(NonEmptyList.of(TransferCoding.chunked, TransferCoding.gzip)),
    )
  }

  test("hasChunked should detect chunked") {
    forAll { (t: `Transfer-Encoding`) =>
      assertEquals(t.hasChunked, t.values.contains_(TransferCoding.chunked))
    }
  }
}
