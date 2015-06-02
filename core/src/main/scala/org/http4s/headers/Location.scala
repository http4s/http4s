package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.util.Writer

object Location extends HeaderKey.Internal[Location] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[Location.HeaderT] =
    parser.LocationHeader.LOCATION(raw.value).toOption
}

final case class Location(uri: Uri) extends Header.Parsed {
  def key = `Location`
  override def value = uri.toString
  def renderValue(writer: Writer): writer.type = writer << uri.toString
}

