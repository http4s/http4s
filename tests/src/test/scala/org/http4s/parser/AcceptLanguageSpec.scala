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

import org.http4s.headers.`Accept-Language`
import org.specs2.mutable.Specification

class AcceptLanguageSpec extends Specification with HeaderParserHelper[`Accept-Language`] {
  def hparse(value: String): ParseResult[`Accept-Language`] =
    `Accept-Language`.parse(value)

  val en = `Accept-Language`(LanguageTag("en"))
  val fr = `Accept-Language`(LanguageTag("fr"))
  val enq5 = `Accept-Language`(LanguageTag("en").withQValue(QValue.q(0.5)))
  val en_cool = `Accept-Language`(LanguageTag("en", "cool"))

  val all = `Accept-Language`(LanguageTag.`*`)
  val ninguno = `Accept-Language`(LanguageTag.`*`.withQValue(QValue.Zero))

  "Accept-Language" should {
    "Give correct value" in {
      en.value must be_==("en")
      enq5.value must be_==("en;q=0.5")
    }

    "Parse properly" in {
      parse(en.value) must be_==(en)
      parse(enq5.value) must be_==(enq5)
      parse(en_cool.value) must be_==(en_cool)
    }
  }
}
