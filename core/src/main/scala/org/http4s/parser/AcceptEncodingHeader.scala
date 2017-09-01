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

import org.http4s.internal.parboiled2._
import ContentCoding._
import org.http4s.headers.`Accept-Encoding`
import org.http4s.util.CaseInsensitiveString

private[parser] trait AcceptEncodingHeader {
  def ACCEPT_ENCODING(value: String): ParseResult[`Accept-Encoding`] =
    new AcceptEncodingParser(value).parse

  private class AcceptEncodingParser(input: ParserInput)
      extends Http4sHeaderParser[`Accept-Encoding`](input) {

    def entry: Rule1[`Accept-Encoding`] = rule {
      oneOrMore(EncodingRangeDecl).separatedBy(ListSep) ~ EOL ~> { xs: Seq[ContentCoding] =>
        `Accept-Encoding`(xs.head, xs.tail: _*)
      }
    }

    def EncodingRangeDecl: Rule1[ContentCoding] = rule {
      (EncodingRangeDef ~ EncodingQuality) ~> { (coding: ContentCoding, q: QValue) =>
        if (q == org.http4s.QValue.One) coding
        else coding.withQValue(q)
      }
    }

    def EncodingRangeDef: Rule1[ContentCoding] = rule {
      "*" ~ push(`*`) | Token ~> { s: String =>
        val cis = CaseInsensitiveString(s)
        org.http4s.ContentCoding.getOrElseCreate(cis)
      }
    }

    def EncodingQuality: Rule1[QValue] = rule {
      ";" ~ OptWS ~ "q" ~ "=" ~ QValue | push(org.http4s.QValue.One)
    }
  }
}
