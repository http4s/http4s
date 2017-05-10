package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `Proxy-Authorization` extends HeaderKey.Internal[`Proxy-Authorization`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`Proxy-Authorization`] =
    HttpHeaderParser.PROXY_AUTHORIZATION(s)
}

final case class `Proxy-Authorization`(credentials: Credentials) extends Header.Parsed {
  override def key: `Proxy-Authorization`.type = `Proxy-Authorization`
  override def renderValue(writer: Writer): writer.type = credentials.render(writer)
}
