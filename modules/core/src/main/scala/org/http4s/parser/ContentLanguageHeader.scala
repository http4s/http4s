/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

package parser

import org.http4s.internal.parboiled2._

private[parser] trait ContentLanguageHeader {
  def CONTENT_LANGUAGE(value: String): ParseResult[headers.`Content-Language`] =
    new ContentLanguageParser(value).parse

  private class ContentLanguageParser(value: String)
      extends Http4sHeaderParser[headers.`Content-Language`](value) {
    def entry: Rule1[headers.`Content-Language`] =
      rule {
        oneOrMore(languageTag).separatedBy(ListSep) ~> { (tags: Seq[LanguageTag]) =>
          headers.`Content-Language`(tags.head, tags.tail: _*)
        }
      }

    def languageTag: Rule1[LanguageTag] =
      rule {
        capture(oneOrMore(Alpha)) ~ zeroOrMore("-" ~ Token) ~> {
          (main: String, sub: collection.Seq[String]) =>
            LanguageTag(main, org.http4s.QValue.One, sub.toList)
        }
      }
  }
}
