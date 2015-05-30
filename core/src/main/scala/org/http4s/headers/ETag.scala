package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.util.Writer

object ETag extends HeaderKey.Internal[ETag] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[ETag.HeaderT] =
    parser.SimpleHeaders.ETAG(raw.value).toOption
}

final case class ETag(tag: String) extends Header.Parsed {
  def key: HeaderKey = ETag
  override def value: String = tag
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

