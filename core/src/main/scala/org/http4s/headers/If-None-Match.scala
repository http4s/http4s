package org.http4s
package headers

import org.http4s.util.Writer

object `If-None-Match` extends HeaderKey.Internal[`If-None-Match`] with HeaderKey.Singleton

case class `If-None-Match`(tag: String) extends Header.Parsed {
  override def key: HeaderKey = `If-None-Match`
  override def value: String = tag
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

