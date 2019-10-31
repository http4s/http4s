package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Link extends HeaderKey.Internal[Link] {
  override def parse(s: String): ParseResult[Link] =
    HttpHeaderParser.LINK(s)
}

final case class Link(
    uri: Uri,
    rel: Option[String] = None,
    rev: Option[String] = None,
    title: Option[String] = None,
    `type`: Option[MediaRange] = None)
    extends Header.Parsed {
  def key: Link.type = Link
  override lazy val value = super.value
  override def renderValue(writer: Writer): writer.type = {
    writer << "<" << uri.toString << ">"
    rel.foreach(writer.append("; rel=").append(_))
    rev.foreach(writer.append("; rev=").append(_))
    title.foreach(writer.append("; title=").append(_))
    `type`.foreach { m =>
      writer.append("; type=")
      HttpCodec[MediaRange].render(writer, m)
    }
    writer
  }
}
