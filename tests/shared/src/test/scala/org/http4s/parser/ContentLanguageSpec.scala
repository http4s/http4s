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
import org.http4s.syntax.all._

class ContentLanguageSpec extends Http4sSuite with HeaderParserHelper[`Content-Language`] {

  private val en = `Content-Language`(LanguageTag("en"))
  private val en_IN = `Content-Language`(LanguageTag("en", "IN"))
  private val en_IN_en_US = `Content-Language`(LanguageTag("en", "IN"), LanguageTag("en", "US"))
  private val multi_lang =
    `Content-Language`(LanguageTag("en"), LanguageTag("fr"), LanguageTag("da"), LanguageTag("id"))

  test("Content-Language should Give correct value") {
    assertEquals(en.value, "en")
    assertEquals(en_IN.value, "en-IN")
    assertEquals(en_IN_en_US.value, "en-IN, en-US")
    assertEquals(multi_lang.value, "en, fr, da, id")
  }

  test("Content-Language should Parse Properly") {
    assertEquals(roundTrip(en), en)
    assertEquals(roundTrip(en_IN), en_IN)
    assertEquals(roundTrip(en_IN_en_US), en_IN_en_US)
    assertEquals(roundTrip(multi_lang), multi_lang)
  }

}
