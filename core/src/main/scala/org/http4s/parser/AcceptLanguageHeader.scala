/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AcceptLanguageHeader.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s
package parser

import org.http4s.internal.parboiled2._
import org.http4s.QValue.QValueParser

private[parser] trait AcceptLanguageHeader {
  def ACCEPT_LANGUAGE(value: String): ParseResult[headers.`Accept-Language`] =
    new AcceptLanguageParser(value).parse

  private class AcceptLanguageParser(value: String)
      extends Http4sHeaderParser[headers.`Accept-Language`](value)
      with MediaRange.MediaRangeParser
      with QValueParser {
    def entry: Rule1[headers.`Accept-Language`] = rule {
      oneOrMore(languageTag).separatedBy(ListSep) ~> { (tags: Seq[LanguageTag]) =>
        headers.`Accept-Language`(tags.head, tags.tail: _*)
      }
    }

    def languageTag: Rule1[LanguageTag] = rule {
      capture(oneOrMore(Alpha)) ~ zeroOrMore("-" ~ Token) ~ QualityValue ~> {
        (main: String, sub: collection.Seq[String], q: QValue) =>
          LanguageTag(main, q, sub.toList)
      }
    }
  }
}
