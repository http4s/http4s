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

class AcceptLanguageSuite extends HeaderLaws {
  private val english = LanguageTag("en")
  private val spanish = LanguageTag("es")

  checkAll("AcceptLanguage", headerLaws[`Accept-Language`])

  test("is satisfied by a language tag if the q value is > 0") {
    forAll { (h: `Accept-Language`, cc: LanguageTag) =>
      h.qValue(cc) > QValue.Zero ==> h.satisfiedBy(cc)
    }
  }

  test("is not satisfied by a language tag if the q value is 0") {
    forAll { (h: `Accept-Language`, cc: LanguageTag) =>
      !`Accept-Language`(h.values.map(_.copy(q = QValue.Zero))).satisfiedBy(cc)
    }
  }

  test("matches most specific tag") {
    val acceptLanguage = `Accept-Language`(
      LanguageTag.*,
      LanguageTag("de", qValue"0.3", List("DE", "1996")),
      LanguageTag("de", qValue"0.1"),
      LanguageTag("de", qValue"0.2", List("DE")),
    )
    assertEquals(acceptLanguage.qValue(LanguageTag("de")), qValue"0.1")
    assertEquals(acceptLanguage.qValue(LanguageTag("de", subTags = List("DE"))), qValue"0.2")
    assertEquals(
      acceptLanguage.qValue(LanguageTag("de", subTags = List("DE", "1996"))),
      qValue"0.3",
    )
    assertEquals(
      acceptLanguage.qValue(LanguageTag("de", subTags = List("DE", "2017"))),
      qValue"0.2",
    )
  }

  test("matches splatted if primary tag not present") {
    val acceptLanguage = `Accept-Language`(LanguageTag.*, spanish.withQValue(qValue"0.5"))
    assertEquals(acceptLanguage.qValue(english), QValue.One)
  }

  test("rejects language tag matching primary tag with q=0") {
    val acceptLanguage = `Accept-Language`(LanguageTag.*, english.withQValue(QValue.Zero))
    assertEquals(acceptLanguage.qValue(english), QValue.Zero)
  }

  test("rejects language tag matching splat with q=0") {
    val acceptLanguage =
      `Accept-Language`(LanguageTag.*.withQValue(QValue.Zero), spanish.withQValue(qValue"0.5"))
    assertEquals(acceptLanguage.qValue(english), QValue.Zero)
  }

  test("rejects unmatched language tag") {
    val acceptLanguage = `Accept-Language`(spanish.withQValue(qValue"0.5"))
    assertEquals(acceptLanguage.qValue(english), QValue.Zero)
  }
}
