/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AcceptEncodingHeader.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s
package parser

import org.http4s.ContentCoding.ContentCodingParser
import org.http4s.headers.`Accept-Encoding`
import org.http4s.internal.parboiled2._

private[parser] trait AcceptEncodingHeader {
  def ACCEPT_ENCODING(value: String): ParseResult[`Accept-Encoding`] =
    new AcceptEncodingParser(value).parse

  private class AcceptEncodingParser(input: ParserInput)
      extends Http4sHeaderParser[`Accept-Encoding`](input)
      with ContentCodingParser {
    def entry: Rule1[`Accept-Encoding`] =
      rule {
        oneOrMore(EncodingRangeDecl).separatedBy(ListSep) ~ EOL ~> { (xs: Seq[ContentCoding]) =>
          `Accept-Encoding`(xs.head, xs.tail: _*)
        }
      }
  }
}
