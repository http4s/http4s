package org.http4s
package headers

import org.http4s.util.Writer

object ETag extends HeaderKey.Internal[ETag] with HeaderKey.Singleton {
  case class EntityTag(tag: String, weak: Boolean = false) {
    override def toString() = {
      if (weak) "W/\"" + tag + '"'
      else "\"" + tag + '"'
    }
  }
}

final case class ETag(tag: ETag.EntityTag) extends Header.Parsed {
  def key: HeaderKey = ETag
  override def value: String = tag.toString()
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}


