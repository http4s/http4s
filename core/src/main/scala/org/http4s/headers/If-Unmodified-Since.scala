package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderer, Writer}

object `If-Unmodified-Since`
    extends HeaderKey.Internal[`If-Unmodified-Since`]
    with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`If-Unmodified-Since`] =
    HttpHeaderParser.IF_UNMODIFIED_SINCE(s)
}

final case class `If-Unmodified-Since`(date: HttpDate) extends Header.Parsed {
  override def key: `If-Unmodified-Since`.type = `If-Unmodified-Since`
  override def value: String = Renderer.renderString(date)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
