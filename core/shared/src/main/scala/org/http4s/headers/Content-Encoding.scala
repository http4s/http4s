package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `Content-Encoding` extends HeaderKey.Internal[`Content-Encoding`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`Content-Encoding`] =
    HttpHeaderParser.CONTENT_ENCODING(s)
}

final case class `Content-Encoding`(contentCoding: ContentCoding) extends Header.Parsed {
  override def key: `Content-Encoding`.type = `Content-Encoding`
  override def renderValue(writer: Writer): writer.type = contentCoding.render(writer)
}
