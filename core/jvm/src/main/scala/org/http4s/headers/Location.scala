package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Location extends HeaderKey.Internal[Location] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[Location] =
    HttpHeaderParser.LOCATION(s)
}

final case class Location(uri: Uri) extends Header.Parsed {
  def key: `Location`.type = `Location`
  override def value: String = uri.toString
  def renderValue(writer: Writer): writer.type = writer << uri.toString
}
