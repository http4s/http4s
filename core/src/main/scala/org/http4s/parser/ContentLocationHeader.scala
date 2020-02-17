package org.http4s.parser

import org.http4s._
import org.http4s.headers.`Content-Location`

trait ContentLocationHeader {
  def CONTENT_LOCATION(value: String): ParseResult[`Content-Location`] = new ContentLocationParser(value).parse

  private class ContentLocationParser(value: String) extends UriHeaderParser[`Content-Location`](value) {
    override def fromUri(uri: Uri): `Content-Location` = `Content-Location`(uri)
  }
}
