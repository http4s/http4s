package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

import scalaz.NonEmptyList

object Cookie extends HeaderKey.Internal[Cookie] with HeaderKey.Recurring {
  override def fromString(s: String): ParseResult[Cookie] =
    HttpHeaderParser.COOKIE(s)
}


final case class Cookie(values: NonEmptyList[org.http4s.Cookie]) extends Header.RecurringRenderable {
  override def key = Cookie
  type Value = org.http4s.Cookie
  override def renderValue(writer: Writer): writer.type = {
    values.head.render(writer)
    values.tail.foreach( writer << "; " << _ )
    writer
  }
}

