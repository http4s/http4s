package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `X-B3-ParentSpanId`
    extends HeaderKey.Internal[`X-B3-ParentSpanId`]
    with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`X-B3-ParentSpanId`] =
    HttpHeaderParser.X_B3_PARENTSPANID(s)

}

final case class `X-B3-ParentSpanId`(id: Long) extends Header.Parsed {
  override def key: `X-B3-ParentSpanId`.type = `X-B3-ParentSpanId`

  override def renderValue(writer: Writer): writer.type =
    xB3RenderValueImpl(writer, id)
}
