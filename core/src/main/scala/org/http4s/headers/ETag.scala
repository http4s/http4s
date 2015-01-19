package org.http4s
package headers

import org.http4s.util.Writer

object ETag extends HeaderKey.Internal[ETag] with HeaderKey.Singleton

final case class ETag(tag: String) extends Header.Parsed {
  def key: HeaderKey = ETag
  override def value: String = tag
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

