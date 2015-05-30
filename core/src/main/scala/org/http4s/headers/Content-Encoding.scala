package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.util.Writer

object `Content-Encoding` extends HeaderKey.Internal[`Content-Encoding`] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[`Content-Encoding`.HeaderT] =
    parser.SimpleHeaders.CONTENT_ENCODING(raw.value).toOption
}

final case class `Content-Encoding`(contentCoding: ContentCoding) extends Header.Parsed {
  override def key = `Content-Encoding`
  override def renderValue(writer: Writer): writer.type = contentCoding.render(writer)
}

