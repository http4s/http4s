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
import cats.syntax.foldable._
import org.http4s.util.Renderer
import org.http4s.v2.Header
import org.scalacheck.Prop.forAll
import org.http4s.laws.discipline.ArbitraryInstances._

class TransferEncodingSuite extends HeaderLaws {
  //checkAll("TransferEncoding", headerLaws(`Transfer-Encoding`))

  // TODO Temporal class providing methods which will be replaced by syntax later on
  implicit class TemporalSyntax(header: `Transfer-Encoding`) {
    def renderString: String = {
      val raw = implicitly[Header.Select[`Transfer-Encoding`]].toRaw(header)
      Renderer.renderString(raw)
    }
  }

  test("render should include all the encodings") {
    assertEquals(
      `Transfer-Encoding`(TransferCoding.chunked).renderString,
      "Transfer-Encoding: chunked")
    assertEquals(
      `Transfer-Encoding`(TransferCoding.chunked, TransferCoding.gzip).renderString,
      "Transfer-Encoding: chunked, gzip")
  }

  test("parse should accept single codings") {
    assertEquals(
      v2.Header[`Transfer-Encoding`].parse("chunked").map(_.values),
      Right(NonEmptyList.one(TransferCoding.chunked)))
  }
  test("parse should accept multiple codings") {
    assertEquals(
      v2.Header[`Transfer-Encoding`].parse("chunked, gzip").map(_.values),
      Right(NonEmptyList.of(TransferCoding.chunked, TransferCoding.gzip)))
    assertEquals(
      v2.Header[`Transfer-Encoding`].parse("chunked,gzip").map(_.values),
      Right(NonEmptyList.of(TransferCoding.chunked, TransferCoding.gzip)))
  }

  test("hasChunked should detect chunked") {
    forAll { (t: `Transfer-Encoding`) =>
      assertEquals(t.hasChunked, (t.values.contains_(TransferCoding.chunked)))
    }
  }
}
