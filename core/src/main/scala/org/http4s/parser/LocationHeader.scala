package org.http4s.parser

import org.http4s.headers.Location
import org.http4s._

trait LocationHeader {

  def LOCATION(value: String): ParseResult[Location] = new LocationParser(value).parse

  private class LocationParser(value: String) extends UriHeaderParser[Location](value) {
    override def fromUri(uri: Uri): Location = Location(uri)
  }
}
