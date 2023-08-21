/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package headers

import cats.parse.Parser
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci._

object `Content-Type` {
  def apply(mediaType: MediaType, charset: Option[Charset]): `Content-Type` =
    new `Content-Type`(mediaType, charset)

  def apply(mediaType: MediaType, charset: Charset): `Content-Type` =
    apply(mediaType, Some(charset))
  def apply(mediaType: MediaType): `Content-Type` = apply(mediaType, None)

  def parse(s: String): ParseResult[`Content-Type`] =
    ParseResult.fromParser(parser, "Invalid Content-Type header")(s)

  private[http4s] val parser: Parser[`Content-Type`] =
    (MediaRange.parser ~ MediaRange.mediaTypeExtensionParser.rep0).flatMap {
      case (range: MediaRange, exts: Seq[(String, String)]) =>
        val mediaTypeParser = range match {
          case m: MediaType => Parser.pure(m)
          case _ =>
            Parser.failWith("Content-Type header doesn't support media ranges")
        }

        val (ext, charset) =
          exts.foldLeft((Map.empty[String, String], None: Option[Charset])) {
            case ((ext, charset), p @ (k, v)) =>
              if (k == "charset") (ext, Charset.fromString(v).toOption)
              else (ext + p, charset)
          }

        mediaTypeParser.map { mediaType =>
          `Content-Type`(if (ext.isEmpty) mediaType else mediaType.withExtensions(ext), charset)
        }
    }

  implicit val headerInstance: Header[`Content-Type`, Header.Single] =
    Header.createRendered(
      ci"Content-Type",
      h =>
        new Renderable {
          def render(writer: Writer): writer.type =
            h.charset match {
              case Some(cs) => writer << h.mediaType << "; charset=" << cs
              case _ => MediaRange.http4sHttpCodecForMediaRange.render(writer, h.mediaType)
            }
        },
      parse,
    )

}

/** {{{
  *   The "Content-Type" header field indicates the media type of the
  *   associated representation: either the representation enclosed in the
  *   message payload or the selected representation, as determined by the
  *   message semantics.
  * }}}
  *
  * [[https://datatracker.ietf.org/doc/html/rfc7231#section-3.1.1.5 RFC-7231]]
  */
final case class `Content-Type` private[headers] (mediaType: MediaType, charset: Option[Charset]) { // scalafix:ok; private for API ergonomics, not correctness
  def withMediaType(mediaType: MediaType): `Content-Type` =
    if (mediaType != this.mediaType) copy(mediaType = mediaType) else this
  def withCharset(charset: Charset): `Content-Type` =
    if (noCharsetDefined || charset != this.charset.get) copy(charset = Some(charset)) else this
  def withoutDefinedCharset: `Content-Type` =
    if (isCharsetDefined) copy(charset = None) else this

  def isCharsetDefined: Boolean = charset.isDefined
  def noCharsetDefined: Boolean = charset.isEmpty
}
