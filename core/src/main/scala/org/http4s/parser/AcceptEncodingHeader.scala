/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AcceptEncodingHeader.scala
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

import org.http4s.ContentCoding.ContentCodingParser
import org.http4s.headers.`Accept-Encoding`
import org.http4s.internal.parboiled2._

private[parser] trait AcceptEncodingHeader {
  def ACCEPT_ENCODING(value: String): ParseResult[`Accept-Encoding`] =
    new AcceptEncodingParser(value).parse

  private class AcceptEncodingParser(input: ParserInput)
      extends Http4sHeaderParser[`Accept-Encoding`](input)
      with ContentCodingParser {
    def entry: Rule1[`Accept-Encoding`] = rule {
      oneOrMore(EncodingRangeDecl).separatedBy(ListSep) ~ EOL ~> { xs: Seq[ContentCoding] =>
        `Accept-Encoding`(xs.head, xs.tail: _*)
      }
    }
  }
}
