/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/ContentTypeHeader.scala
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
import headers.`Content-Type`

private[parser] trait ContentTypeHeader {

  def CONTENT_TYPE(value: String): ParseResult[`Content-Type`] =
    new ContentTypeParser(value).parse

  private class ContentTypeParser(input: ParserInput) extends Http4sHeaderParser[`Content-Type`](input) with MediaParser {
    def entry: org.parboiled2.Rule1[`Content-Type`] = rule {
      (MediaRangeDef ~ optional(zeroOrMore(MediaTypeExtension)) ~ EOL) ~> { (range: MediaRange, exts: Option[Seq[(String, String)]]) =>
        val mediaType = range match {
          case m: MediaType => m
          case m =>
            throw new ParseFailure("Invalid Content-Type header", "Content-Type header doesn't support media ranges")
        }

        var charset: Option[Charset] = None
        var ext = Map.empty[String, String]

        exts.foreach(_.foreach { case p @ (k, v) =>
          if (k == "charset") charset = Charset.fromString(v).toOption
          else ext += p
        })

        `Content-Type`(if (ext.isEmpty) mediaType else mediaType.withExtensions(ext), charset)
      }
    }
  }
}
