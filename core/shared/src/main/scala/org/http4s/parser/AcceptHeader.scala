/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AcceptHeader.scala
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

import org.http4s.headers.{Accept, MediaRangeAndQValue}
import org.http4s.internal.parboiled2._

private[parser] trait AcceptHeader {

  def ACCEPT(value: String): ParseResult[headers.Accept] = new AcceptParser(value).parse

  private class AcceptParser(value: String)
      extends Http4sHeaderParser[Accept](value)
      with MediaRange.MediaRangeParser {

    def entry: Rule1[headers.Accept] = rule {
      oneOrMore(FullRange).separatedBy("," ~ OptWS) ~ EOL ~> { xs: Seq[MediaRangeAndQValue] =>
        Accept(xs.head, xs.tail: _*)
      }
    }

    def FullRange: Rule1[MediaRangeAndQValue] = rule {
      (MediaRangeDef ~ optional(QAndExtensions)) ~> {
        (mr: MediaRange, params: Option[(QValue, Seq[(String, String)])]) =>
          val (qValue, extensions) = params.getOrElse((org.http4s.QValue.One, Seq.empty))
          mr.withExtensions(extensions.toMap).withQValue(qValue)
      }
    }

    def QAndExtensions: Rule1[(QValue, Seq[(String, String)])] = rule {
      AcceptParams | (oneOrMore(MediaTypeExtension) ~> { s: Seq[(String, String)] =>
        (org.http4s.QValue.One, s)
      })
    }

    def AcceptParams: Rule1[(QValue, Seq[(String, String)])] = rule {
      (";" ~ OptWS ~ "q" ~ "=" ~ QValue ~ zeroOrMore(MediaTypeExtension)) ~> (
        (
          _: QValue,
          _: Seq[(String, String)]))
    }
  }
}
