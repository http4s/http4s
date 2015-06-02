package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.Writer

object `Last-Modified` extends HeaderKey.Internal[`Last-Modified`] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[`Last-Modified`] = {
    new Http4sHeaderParser[`Last-Modified`](raw.value) {
      def entry = rule {
        HttpDate ~ EOL ~> (`Last-Modified`(_))
      }
    }.parse.toOption
  }
}

final case class `Last-Modified`(date: DateTime) extends Header.Parsed {
  override def key = `Last-Modified`
  override def value = date.toRfc1123DateTimeString
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

