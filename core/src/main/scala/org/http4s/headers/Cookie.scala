package org.http4s
package headers

import cats.data.NonEmptyList
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Cookie extends HeaderKey.Internal[Cookie] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[Cookie] =
    HttpHeaderParser.COOKIE(s)
}

final case class Cookie(values: NonEmptyList[RequestCookie]) extends Header.RecurringRenderable {
  override def key: Cookie.type = Cookie
  type Value = RequestCookie
  override def renderValue(writer: Writer): writer.type = {
    values.head.render(writer)
    values.tail.foreach(writer << "; " << _)
    writer
  }
}
