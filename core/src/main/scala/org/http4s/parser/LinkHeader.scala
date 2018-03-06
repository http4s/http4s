package org.http4s.parser

import org.http4s.{ParseResult, Uri}
import org.http4s.headers.Link
import org.http4s.internal.parboiled2.Rule1

trait LinkHeader {
  def LINK(value: String): ParseResult[Link] = new LinkParser(value).parse

  private class LinkParser(value: String) extends UriHeaderParser[Link](value) {
    override def fromUri(uri: Uri): Link = Link(uri)

    override def entry: Rule1[Link] = rule {
      "<" ~ super[UriHeaderParser].entry ~ ">"
    }
  }
}
