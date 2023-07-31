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
import org.http4s.syntax.all._

class AcceptLanguageSuite extends Http4sSuite with HeaderParserHelper[`Accept-Language`] {

  private val en = `Accept-Language`(LanguageTag("en"))
  private val enq5 = `Accept-Language`(LanguageTag("en").withQValue(qValue"0.5"))
  private val en_cool = `Accept-Language`(LanguageTag("en", "cool"))
  private val en_mult = `Accept-Language`(LanguageTag("en", "a", "b"))

  private val all = `Accept-Language`(LanguageTag.`*`)
  private val ninguno = `Accept-Language`(LanguageTag.`*`.withQValue(QValue.Zero))

  test("Accept-Language should Give correct value") {
    assertEquals(en.value, "en")
    assertEquals(enq5.value, "en;q=0.5")
    assertEquals(enq5.value, "en;q=0.5")
    assertEquals(en_cool.value, "en-cool")
    assertEquals(en_mult.value, "en-a-b")
    assertEquals(all.value, "*")
    assertEquals(ninguno.value, "*;q=0")
  }

  test("Accept-Language should Parse properly") {
    assertEquals(parse(en.value), en)
    assertEquals(parse(enq5.value), enq5)
    assertEquals(parse(en_cool.value), en_cool)
    assertEquals(parse(en_mult.value), en_mult)
    assertEquals(parse(all.value), all)
    assertEquals(parse(ninguno.value), ninguno)
  }

}
