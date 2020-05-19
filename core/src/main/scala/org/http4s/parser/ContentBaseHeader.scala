package org.http4s.parser

import org.http4s._
import org.http4s.headers.`Content-Base`

trait ContentBaseHeader {
  def CONTENT_BASE(value: String): ParseResult[`Content-Base`] =
    new ContentBaseParser(value).parse

  private class ContentBaseParser(value: String) extends UriHeaderParser[`Content-Base`](value) {
    override def fromUri(uri: Uri): `Content-Base` = `Content-Base`(uri)
  }
}
