package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Referer extends HeaderKey.Internal[Referer] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[Referer] =
    HttpHeaderParser.REFERER(s)
}

final case class Referer(uri: Uri) extends Header.Parsed {
  override def key: `Referer`.type = `Referer`
  override def renderValue(writer: Writer): writer.type = uri.render(writer)
}
