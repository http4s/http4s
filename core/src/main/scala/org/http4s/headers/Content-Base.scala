package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `Content-Base` extends HeaderKey.Internal[`Content-Base`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`Content-Base`] =
    HttpHeaderParser.CONTENT_BASE(s)
}

/**
  * Content-Base header
  * https://tools.ietf.org/html/rfc2068#section-14.11
  */
final case class `Content-Base`(uri: Uri) extends Header.Parsed {
  def key: `Content-Base`.type = `Content-Base`
  override def value: String = uri.toString
  def renderValue(writer: Writer): writer.type = writer << uri.toString
}
