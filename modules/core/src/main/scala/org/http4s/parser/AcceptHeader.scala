/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AcceptHeader.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
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
    def entry: Rule1[headers.Accept] =
      rule {
        oneOrMore(FullRange).separatedBy("," ~ OptWS) ~ EOL ~> { (xs: Seq[MediaRangeAndQValue]) =>
          Accept(xs.head, xs.tail: _*)
        }
      }

    def FullRange: Rule1[MediaRangeAndQValue] =
      rule {
        (MediaRangeDef ~ optional(QAndExtensions)) ~> {
          (mr: MediaRange, params: Option[(QValue, collection.Seq[(String, String)])]) =>
            val (qValue, extensions) =
              params.getOrElse((org.http4s.QValue.One, collection.Seq.empty))
            mr.withExtensions(extensions.toMap).withQValue(qValue)
        }
      }

    def QAndExtensions: Rule1[(QValue, collection.Seq[(String, String)])] =
      rule {
        AcceptParams | (oneOrMore(MediaTypeExtension) ~> { (s: collection.Seq[(String, String)]) =>
          (org.http4s.QValue.One, s)
        })
      }

    def AcceptParams: Rule1[(QValue, collection.Seq[(String, String)])] =
      rule {
        (";" ~ OptWS ~ "q" ~ "=" ~ QValue ~ zeroOrMore(MediaTypeExtension)) ~> (
          (
            _: QValue,
            _: collection.Seq[(String, String)]))
      }
  }
}
