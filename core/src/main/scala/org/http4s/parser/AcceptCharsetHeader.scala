/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AcceptCharsetHeader.scala
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

import org.http4s.batteries._
import org.parboiled2._
import CharsetRange._

private[parser] trait AcceptCharsetHeader {

  def ACCEPT_CHARSET(value: String): ParseResult[headers.`Accept-Charset`] =
    new AcceptCharsetParser(value).parse

  private class AcceptCharsetParser(input: ParserInput) extends Http4sHeaderParser[headers.`Accept-Charset`](input) {
    def entry: Rule1[headers.`Accept-Charset`] = rule {
      oneOrMore(CharsetRangeDecl).separatedBy(ListSep) ~ EOL ~> {xs: Seq[CharsetRange] =>
        headers.`Accept-Charset`(xs.head, xs.tail: _*)
      }
    }

    def CharsetRangeDecl: Rule1[CharsetRange] = rule {
      ("*" ~ CharsetQuality) ~> { q => if (q != org.http4s.QValue.One) `*`.withQValue(q) else `*` } |
      ((Token ~ CharsetQuality) ~> { (s: String, q: QValue) =>
        // TODO handle tokens that aren't charsets
        val c = Charset.fromString(s).valueOr(throw _)
        if (q != org.http4s.QValue.One) c.withQuality(q) else c.toRange
      })
    }

    def CharsetQuality: Rule1[QValue] = rule {
      (";" ~ OptWS ~ "q" ~ "=" ~ QValue) | push(org.http4s.QValue.One)
    }
  }
}
