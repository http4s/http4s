package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `X-B3-TraceId` extends HeaderKey.Internal[`X-B3-TraceId`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`X-B3-TraceId`] =
    HttpHeaderParser.X_B3_TRACEID(s)
}

final case class `X-B3-TraceId`(id: Long) extends Header.Parsed {
  override def key: `X-B3-TraceId`.type = `X-B3-TraceId`

  override def renderValue(writer: Writer): writer.type =
    xB3RenderValueImpl(writer, id)
}
