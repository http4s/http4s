package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.util.Writer

import scalaz.NonEmptyList

object Cookie extends HeaderKey.Internal[Cookie] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[Cookie.HeaderT] =
    parser.CookieHeader.COOKIE(raw.value).toOption
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

