package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

import org.http4s.util.NonEmptyList

object Allow extends HeaderKey.Internal[Allow] with HeaderKey.Singleton {
  def apply(m: Method, ms: Method*): Allow = Allow(NonEmptyList(m, ms:_*))

  override def parse(s: String): ParseResult[Allow] =
    HttpHeaderParser.ALLOW(s)
}

final case class Allow(methods: NonEmptyList[Method]) extends Header.Parsed {
  override def key = Allow
  override def renderValue(writer: Writer): writer.type =
    writer.addStringNel(methods.map(_.name), ", ")
}
