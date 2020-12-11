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

import org.http4s.headers.`Content-Language`
import org.specs2.mutable.Specification

class ContentLanguageSpec extends Specification with HeaderParserHelper[`Content-Language`] {

  override def hparse(value: String): ParseResult[`Content-Language`] =
    HttpHeaderParser.CONTENT_LANGUAGE(value)

  val en = `Content-Language`(LanguageTag("en"))
  val en_IN = `Content-Language`(LanguageTag("en", "IN"))
  val en_IN_en_US = `Content-Language`(LanguageTag("en", "IN"), LanguageTag("en", "US"))
  val multi_lang =
    `Content-Language`(LanguageTag("en"), LanguageTag("fr"), LanguageTag("da"), LanguageTag("id"))

  "Content-Language" should {
    "Give correct value" in {
      en.value must be_==("en")
      en_IN.value must be_==("en-IN")
      en_IN_en_US.value must be_==("en-IN, en-US")
      multi_lang.value must be_==("en, fr, da, id")
    }

    "Parse Properly" in {
      parse(en.value) must be_==(en)
      parse(en_IN.value) must be_==(en_IN)
      parse(en_IN_en_US.value) must be_==(en_IN_en_US)
      parse(multi_lang.value) must be_==(multi_lang)
    }
  }
}
