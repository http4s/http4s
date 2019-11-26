package org.http4s
package headers

import org.http4s.util.{Renderable, Writer}

final case class LinkValue(
    uri: Uri,
    rel: Option[String] = None,
    rev: Option[String] = None,
    title: Option[String] = None,
    `type`: Option[MediaRange] = None)
    extends Renderable {
  override def render(writer: Writer): writer.type = {
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
