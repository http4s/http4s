package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.util.Writer

object `If-Modified-Since` extends HeaderKey.Internal[`If-Modified-Since`] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[`If-Modified-Since`] =
    parser.SimpleHeaders.IF_MODIFIED_SINCE(raw.value).toOption
}

final case class `If-Modified-Since`(date: DateTime) extends Header.Parsed {
  override def key: HeaderKey = `If-Modified-Since`
  override def value: String = date.toRfc1123DateTimeString
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

