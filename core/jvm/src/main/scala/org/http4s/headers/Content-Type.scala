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

final case class `Content-Type` private (mediaType: MediaType, charset: Option[Charset])
    extends Header.Parsed {
  override def key: `Content-Type`.type = `Content-Type`
  override def renderValue(writer: Writer): writer.type = charset match {
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
