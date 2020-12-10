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

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `Content-Type` extends HeaderKey.Internal[`Content-Type`] with HeaderKey.Singleton {
  def apply(mediaType: MediaType, charset: Charset): `Content-Type` =
    apply(mediaType, Some(charset))
  def apply(mediaType: MediaType): `Content-Type` = apply(mediaType, None)

  override def parse(s: String): ParseResult[`Content-Type`] =
    HttpHeaderParser.CONTENT_TYPE(s)
}

/** {{{
  *   The "Content-Type" header field indicates the media type of the
  *   associated representation: either the representation enclosed in the
  *   message payload or the selected representation, as determined by the
  *   message semantics.
  * }}}
  *
  * [[https://tools.ietf.org/html/rfc7231#section-3.1.1.5 RFC-7231]]
  */
final case class `Content-Type` private (mediaType: MediaType, charset: Option[Charset])
    extends Header.Parsed {
  override def key: `Content-Type`.type = `Content-Type`
  override def renderValue(writer: Writer): writer.type =
    charset match {
      case Some(cs) => writer << mediaType << "; charset=" << cs
      case _ => MediaRange.http4sHttpCodecForMediaRange.render(writer, mediaType)
    }

  def withMediaType(mediaType: MediaType): `Content-Type` =
    if (mediaType != this.mediaType) copy(mediaType = mediaType) else this
  def withCharset(charset: Charset): `Content-Type` =
    if (noCharsetDefined || charset != this.charset.get) copy(charset = Some(charset)) else this
  def withoutDefinedCharset: `Content-Type` =
    if (isCharsetDefined) copy(charset = None) else this

  def isCharsetDefined: Boolean = charset.isDefined
  def noCharsetDefined: Boolean = charset.isEmpty
}
