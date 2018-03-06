package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Link extends HeaderKey.Internal[Link]  {
  override def parse(s: String): ParseResult[Link] =
    HttpHeaderParser.LINK(s)
}

final case class Link(uri: Uri) extends Header.Parsed {
  def key: Link.type = Link
  override lazy val value = super.value
  override def renderValue(writer: Writer): writer.type = {
    writer << "<" << uri.toString << ">"
    writer
  }
}
