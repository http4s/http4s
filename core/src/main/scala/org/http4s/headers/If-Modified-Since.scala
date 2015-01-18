package org.http4s
package headers

import org.http4s.util.Writer

object `If-Modified-Since` extends HeaderKey.Internal[`If-Modified-Since`] with HeaderKey.Singleton

final case class `If-Modified-Since`(date: DateTime) extends ParsedHeader {
  override def key: HeaderKey = `If-Modified-Since`
  override def value: String = date.toRfc1123DateTimeString
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

