package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer
import cats.Show

object ETag extends HeaderKey.Internal[ETag] with HeaderKey.Singleton {
  final case class EntityTag(tag: String, weak: Boolean = false) {
    override def toString() =
      if (weak) "W/\"" + tag + '"'
      else "\"" + tag + '"'
  }

  object EntityTag {
    implicit val http4sShowForEntityTag: Show[EntityTag] =
      Show.fromToString
  }

  def apply(tag: String, weak: Boolean = false): ETag = ETag(EntityTag(tag, weak))

  override def parse(s: String): ParseResult[ETag] =
    HttpHeaderParser.ETAG(s)

}

final case class ETag(tag: ETag.EntityTag) extends Header.Parsed {
  def key: ETag.type = ETag
  override def value: String = tag.toString()
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
