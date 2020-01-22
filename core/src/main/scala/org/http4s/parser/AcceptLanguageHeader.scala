/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AcceptLanguageHeader.scala
 *
 * Copyright (C) 2011-2012 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
      oneOrMore(languageTag).separatedBy(ListSep) ~> { tags: Seq[LanguageTag] =>
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
