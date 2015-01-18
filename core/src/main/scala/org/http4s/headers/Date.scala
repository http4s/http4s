package org.http4s
package headers

import org.http4s.util.Writer

object Date extends HeaderKey.Internal[Date] with HeaderKey.Singleton

final case class Date(date: DateTime) extends ParsedHeader {
  def key = `Date`
  override def value = date.toRfc1123DateTimeString
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

