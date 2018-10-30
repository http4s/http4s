package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderer, Writer}

object `Last-Modified` extends HeaderKey.Internal[`Last-Modified`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`Last-Modified`] =
    HttpHeaderParser.LAST_MODIFIED(s)
}

final case class `Last-Modified`(date: HttpDate) extends Header.Parsed {
  override def key: `Last-Modified`.type = `Last-Modified`
  override def value: String = Renderer.renderString(date)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
