package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.Writer

object `If-None-Match` extends HeaderKey.Internal[`If-None-Match`] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[`If-None-Match`] = {
    new Http4sHeaderParser[`If-None-Match`](raw.value) {
      def entry = rule {
        capture(zeroOrMore(AlphaNum)) ~> (`If-None-Match`(_))
      }
    }.parse.toOption
  }
}

case class `If-None-Match`(tag: String) extends Header.Parsed {
  override def key: HeaderKey = `If-None-Match`
  override def value: String = tag
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

