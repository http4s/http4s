package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Authorization extends HeaderKey.Internal[Authorization] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[Authorization] =
    HttpHeaderParser.AUTHORIZATION(s)
}

final case class Authorization(credentials: Credentials) extends Header.Parsed {
  override def key: `Authorization`.type = `Authorization`
  override def renderValue(writer: Writer): writer.type = credentials.render(writer)
}
