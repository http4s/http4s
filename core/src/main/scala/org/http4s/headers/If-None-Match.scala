package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.util.Writer

object `If-None-Match` extends HeaderKey.Internal[`If-None-Match`] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[`If-None-Match`.HeaderT] =
    parser.SimpleHeaders.IF_NONE_MATCH(raw.value).toOption
}

case class `If-None-Match`(tag: String) extends Header.Parsed {
  override def key: HeaderKey = `If-None-Match`
  override def value: String = tag
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

