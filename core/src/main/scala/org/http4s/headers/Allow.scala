package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.util.Writer

import scalaz.NonEmptyList

object Allow extends HeaderKey.Internal[Allow] with HeaderKey.Singleton {

  override protected def parseHeader(raw: Raw): Option[Allow.HeaderT] =
    parser.SimpleHeaders.ALLOW(raw.value).toOption

  def apply(m: Method, ms: Method*): Allow = Allow(NonEmptyList(m, ms:_*))
}

case class Allow(methods: NonEmptyList[Method]) extends Header.Parsed {
  override def key = Allow
  override def renderValue(writer: Writer): writer.type =
    writer.addStrings(methods.list.map(_.name), ", ")
}
