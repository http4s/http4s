/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/ContentTypeHeader.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s
package parser

import org.http4s.headers.`Content-Type`
import org.http4s.internal.parboiled2._

private[parser] trait ContentTypeHeader {
  def CONTENT_TYPE(value: String): ParseResult[`Content-Type`] =
    new ContentTypeParser(value).parse

  private class ContentTypeParser(input: ParserInput)
      extends Http4sHeaderParser[`Content-Type`](input)
      with MediaRange.MediaRangeParser {
    def entry: org.http4s.internal.parboiled2.Rule1[`Content-Type`] = rule {
      (MediaRangeDef ~ optional(zeroOrMore(MediaTypeExtension)) ~ EOL) ~> {
        (range: MediaRange, exts: Option[collection.Seq[(String, String)]]) =>
          val mediaType = range match {
            case m: MediaType => m
            case _ =>
              throw new ParseFailure(
                "Invalid Content-Type header",
                "Content-Type header doesn't support media ranges")
          }

          var charset: Option[Charset] = None
          var ext = Map.empty[String, String]

          exts.foreach(_.foreach {
            case p @ (k, v) =>
              if (k == "charset") charset = Charset.fromString(v).toOption
              else ext += p
          })

          `Content-Type`(if (ext.isEmpty) mediaType else mediaType.withExtensions(ext), charset)
      }
    }
  }
}
