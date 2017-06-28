package org.http4s
package headers

import cats.data.NonEmptyList
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Allow extends HeaderKey.Internal[Allow] with HeaderKey.Singleton {
  def apply(m: Method, ms: Method*): Allow = Allow(NonEmptyList.of(m, ms: _*))

  override def parse(s: String): ParseResult[Allow] =
    HttpHeaderParser.ALLOW(s)
}

final case class Allow(methods: NonEmptyList[Method]) extends Header.Parsed {
  override def key: Allow.type = Allow
  override def renderValue(writer: Writer): writer.type =
    writer.addStringNel(methods.map(_.name), ", ")
}
