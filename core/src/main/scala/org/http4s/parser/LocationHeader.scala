package org.http4s.parser

import java.nio.charset.StandardCharsets

import org.http4s.Header.Location
import org.http4s._
import org.parboiled2._

trait LocationHeader {

  def LOCATION(value: String): ParseResult[Location] = new LocationParser(value).parse

  private class LocationParser(value: String) extends Http4sHeaderParser[Location](value) with Rfc3986Parser {

    override def charset = StandardCharsets.ISO_8859_1

    def entry: Rule1[Header.Location] = rule {
      AbsoluteUri ~> { uri => Location(uri) }
    }

  }

}
