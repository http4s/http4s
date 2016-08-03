package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `Set-Cookie` extends HeaderKey.Internal[`Set-Cookie`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`Set-Cookie`] =
    HttpHeaderParser.SET_COOKIE(s)
}

final case class `Set-Cookie`(cookie: org.http4s.Cookie) extends Header.Parsed {
  override def key: `Set-Cookie`.type = `Set-Cookie`
  override def renderValue(writer: Writer): writer.type = cookie.render(writer)
}

