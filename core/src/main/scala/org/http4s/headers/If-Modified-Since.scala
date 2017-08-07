package org.http4s
package headers

import java.time.Instant

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderer, Writer}

object `If-Modified-Since` extends HeaderKey.Internal[`If-Modified-Since`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`If-Modified-Since`] =
    HttpHeaderParser.IF_MODIFIED_SINCE(s)
}

final case class `If-Modified-Since`(date: Instant) extends Header.Parsed {
  override def key: `If-Modified-Since`.type = `If-Modified-Since`
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

