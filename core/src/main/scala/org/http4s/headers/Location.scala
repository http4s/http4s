package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Location extends HeaderKey.Internal[Location] with HeaderKey.Singleton {
  override def fromString(s: String): ParseResult[Location] =
    HttpHeaderParser.LOCATION(s)
}

final case class Location(uri: Uri) extends Header.Parsed {
  def key = `Location`
  override def value = uri.toString
  def renderValue(writer: Writer): writer.type = writer << uri.toString
}

