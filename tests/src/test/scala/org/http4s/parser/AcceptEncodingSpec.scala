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
import org.specs2.mutable.Specification

class AcceptEncodingSpec extends Specification with HeaderParserHelper[`Accept-Encoding`] {
  def hparse(value: String): ParseResult[`Accept-Encoding`] =
    `Accept-Encoding`.parse(value)

  val gzip = `Accept-Encoding`(ContentCoding.gzip)
  val gzip5 = `Accept-Encoding`(ContentCoding.gzip.withQValue(QValue.q(0.5)))
  val gzip55 = `Accept-Encoding`(ContentCoding.gzip.withQValue(QValue.q(0.55)))
  val gzip555 = `Accept-Encoding`(ContentCoding.gzip.withQValue(QValue.q(0.555)))

  val gzip1 = `Accept-Encoding`(ContentCoding.gzip.withQValue(QValue.q(1.0)))

  "Accept-Encoding parser" should {
    "parse all encodings" in {
      foreach(ContentCoding.standard) { case (_, coding) =>
        parse(coding.renderString).values.head should be_==(coding)
      }
    }
  }

  "Give correct value" in {
    gzip.value must be_==("gzip")
    gzip5.value must be_==("gzip;q=0.5")
    gzip55.value must be_==("gzip;q=0.55")
    gzip555.value must be_==("gzip;q=0.555")

    gzip1.value must be_==("gzip")
  }

  "Parse properly" in {
    parse(gzip.value) must be_==(gzip)
    parse(gzip5.value) must be_==(gzip5)
    parse(gzip555.value) must be_==(gzip555)

    parse("gzip; q=1.0, compress") must be_==(
      `Accept-Encoding`(ContentCoding.gzip, ContentCoding.compress))

    parse(gzip1.value) must be_==(gzip)
  }
}
