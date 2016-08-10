package org.http4s.parser

import java.nio.charset.StandardCharsets

import org.http4s.headers.Location
import org.http4s._
import org.parboiled2._

trait LocationHeader {

  def LOCATION(value: String): ParseResult[Location] = new LocationParser(value).parse

  private class LocationParser(value: String) extends Http4sHeaderParser[Location](value) with Rfc3986Parser {

    override def charset: java.nio.charset.Charset = StandardCharsets.ISO_8859_1

    def entry: Rule1[Location] = rule {
      // https://tools.ietf.org/html/rfc3986#section-4.1
      (AbsoluteUri | RelativeRef) ~> { uri => Location(uri) }
    }

  }

}
