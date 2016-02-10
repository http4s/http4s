package org.http4s
package headers

import org.http4s.util.Writer

import org.http4s.util.NonEmptyList

object Cookie extends HeaderKey.Internal[Cookie] with HeaderKey.Recurring

final case class Cookie(values: NonEmptyList[org.http4s.Cookie]) extends Header.RecurringRenderable {
  override def key = Cookie
  type Value = org.http4s.Cookie
  override def renderValue(writer: Writer): writer.type = {
    values.head.render(writer)
    values.tail.foreach( writer << "; " << _ )
    writer
  }
}

