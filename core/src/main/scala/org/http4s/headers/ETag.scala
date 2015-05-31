package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.Writer

object ETag extends HeaderKey.Internal[ETag] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[ETag] = {
    new Http4sHeaderParser[ETag](raw.value) {
      def entry = rule {
        capture(zeroOrMore(AlphaNum)) ~> (ETag(_))
      }
    }.parse.toOption
  }
}

final case class ETag(tag: String) extends Header.Parsed {
  def key: HeaderKey = ETag
  override def value: String = tag
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

