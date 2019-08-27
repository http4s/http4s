package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Allow extends HeaderKey.Internal[Allow] with HeaderKey.Singleton {
  def apply(ms: Method*): Allow = Allow(ms.toSet)

  override def parse(s: String): ParseResult[Allow] =
    HttpHeaderParser.ALLOW(s)
}

final case class Allow(methods: Set[Method]) extends Header.Parsed {
  override def key: Allow.type = Allow
  override def renderValue(writer: Writer): writer.type =
    writer.addSet[Method](methods, sep = ", ")
}
