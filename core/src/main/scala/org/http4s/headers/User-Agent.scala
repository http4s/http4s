package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `User-Agent` extends HeaderKey.Internal[`User-Agent`] with HeaderKey.Singleton {
  def apply(id: ProductId): `User-Agent` =
    new `User-Agent`(id, Nil)

  override def parse(s: String): ParseResult[`User-Agent`] =
    HttpHeaderParser.USER_AGENT(s)
}

/**
  * User-Agent header
  * https://tools.ietf.org/html/rfc7231#section-5.5.3
  */
final case class `User-Agent`(product: ProductId, rest: List[ProductIdOrComment])
    extends Header.Parsed {
  def key: `User-Agent`.type = `User-Agent`

  override def renderValue(writer: Writer): writer.type = {
    writer << product
    rest.foreach {
      case p: ProductId => writer << ' ' << p
      case ProductComment(c) => writer << ' ' << '(' << c << ')'
    }
    writer
  }
}
