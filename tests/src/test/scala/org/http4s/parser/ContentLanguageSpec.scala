/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
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

  "Content-Language" should {
    "Give correct value" in {
      en.value must be_==("en")
      en_IN.value must be_==("en-IN")
      en_IN_en_US.value must be_==("en-IN, en-US")
    }

    "Parse Properly" in {
      parse(en.value) must be_==(en)
      parse(en_IN.value) must be_==(en_IN)
      parse(en_IN_en_US.value) must be_==(en_IN_en_US)
    }
  }
}
