package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.util.Writer

object `Set-Cookie` extends HeaderKey.Internal[`Set-Cookie`] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[`Set-Cookie`.HeaderT] =
    parser.CookieHeader.SET_COOKIE(raw.value).toOption
}

final case class `Set-Cookie`(cookie: org.http4s.Cookie) extends Header.Parsed {
  override def key = `Set-Cookie`
  override def renderValue(writer: Writer): writer.type = cookie.render(writer)
}

