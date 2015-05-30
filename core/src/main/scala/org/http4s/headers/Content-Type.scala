package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.util.Writer

object `Content-Type` extends HeaderKey.Internal[`Content-Type`] with HeaderKey.Singleton  {

  override protected def parseHeader(raw: Raw): Option[`Content-Type`.HeaderT] =
    parser.ContentTypeHeader.CONTENT_TYPE(raw.value).toOption

  def apply(mediaType: MediaType, charset: Charset): `Content-Type` = apply(mediaType, Some(charset))
  implicit def apply(mediaType: MediaType): `Content-Type` = apply(mediaType, None)
}

final case class `Content-Type`(mediaType: MediaType, definedCharset: Option[Charset]) extends Header.Parsed {
  override def key = `Content-Type`
  override def renderValue(writer: Writer): writer.type = definedCharset match {
    case Some(cs) => writer << mediaType << "; charset=" << cs
    case _        => mediaType.render(writer)
  }

  def withMediaType(mediaType: MediaType) =
    if (mediaType != this.mediaType) copy(mediaType = mediaType) else this
  def withCharset(charset: Charset) =
    if (noCharsetDefined || charset != definedCharset.get) copy(definedCharset = Some(charset)) else this
  def withoutDefinedCharset =
    if (isCharsetDefined) copy(definedCharset = None) else this

  def isCharsetDefined = definedCharset.isDefined
  def noCharsetDefined = definedCharset.isEmpty

  def charset: Charset = definedCharset.getOrElse(Charset.`ISO-8859-1`)
}

