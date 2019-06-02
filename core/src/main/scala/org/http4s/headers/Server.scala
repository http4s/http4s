package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Server extends HeaderKey.Internal[Server] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[Server] =
    HttpHeaderParser.SERVER(s)
}

/**
  * Server header
  * https://tools.ietf.org/html/rfc7231#section-7.4.2
  */
final case class Server(product: ProductId, rest: List[ProductIdOrComment]) extends Header.Parsed {
  def key: Server.type = Server

  override def renderValue(writer: Writer): writer.type = {
    writer << product
    rest.foreach {
      case p: ProductId => writer << ' ' << p
      case ProductComment(c) => writer << ' ' << '(' << c << ')'
    }
    writer
  }

}
