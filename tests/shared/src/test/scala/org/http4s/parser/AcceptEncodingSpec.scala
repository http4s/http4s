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
package parser

import org.http4s.headers.`Accept-Encoding`
import org.http4s.syntax.all._

class AcceptEncodingSpec extends Http4sSuite {
  def parse(value: String): ParseResult[`Accept-Encoding`] =
    `Accept-Encoding`.parse(value)

  private val gzip = `Accept-Encoding`(ContentCoding.gzip)
  private val gzip5 = `Accept-Encoding`(ContentCoding.gzip.withQValue(qValue"0.5"))
  private val gzip55 = `Accept-Encoding`(ContentCoding.gzip.withQValue(qValue"0.55"))
  private val gzip555 = `Accept-Encoding`(ContentCoding.gzip.withQValue(qValue"0.555"))

  private val gzip1 = `Accept-Encoding`(ContentCoding.gzip.withQValue(qValue"1.0"))

  test("Accept-Encoding parser should parse all encodings") {
    ContentCoding.standard.foreach { case (_, coding) =>
      assertEquals(parse(coding.renderString).map(_.values.head), Right(coding))
    }
  }

  test("Give correct value") {
    assertEquals(gzip.value, "gzip")
    assertEquals(gzip5.value, "gzip;q=0.5")
    assertEquals(gzip55.value, "gzip;q=0.55")
    assertEquals(gzip555.value, "gzip;q=0.555")

    assertEquals(gzip1.value, "gzip")
  }

  test("Parse properly") {
    assertEquals(parse(gzip.value), Right(gzip))
    assertEquals(parse(gzip5.value), Right(gzip5))
    assertEquals(parse(gzip555.value), Right(gzip555))

    assertEquals(
      parse("gzip; q=1.0, compress"),
      Right(`Accept-Encoding`(ContentCoding.gzip, ContentCoding.compress)),
    )

    assertEquals(parse(gzip1.value), Right(gzip))
  }
}
