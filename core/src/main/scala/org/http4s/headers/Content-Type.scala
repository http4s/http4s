package org.http4s
package headers

import org.http4s.util.Writer

object `Content-Type` extends HeaderKey.Internal[`Content-Type`] with HeaderKey.Singleton {
  def apply(mediaType: MediaType, charset: Charset): `Content-Type` = apply(mediaType, Some(charset))
  implicit def apply(mediaType: MediaType): `Content-Type` = apply(mediaType, None)
}

final case class `Content-Type`(mediaType: MediaType, charset: Option[Charset]) extends Header.Parsed {
  override def key = `Content-Type`
  override def renderValue(writer: Writer): writer.type = charset match {
    case Some(cs) => writer << mediaType << "; charset=" << cs
    case _        => mediaType.render(writer)
  }

  def withMediaType(mediaType: MediaType) =
    if (mediaType != this.mediaType) copy(mediaType = mediaType) else this
  def withCharset(charset: Charset) =
    if (noCharsetDefined || charset != this.charset.get) copy(charset = Some(charset)) else this
  def withoutDefinedCharset =
    if (isCharsetDefined) copy(charset = None) else this

  def isCharsetDefined = charset.isDefined
  def noCharsetDefined = charset.isEmpty
}

