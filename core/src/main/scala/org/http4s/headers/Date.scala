package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.Writer

object Date extends HeaderKey.Internal[Date] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[Date] = {
    new Http4sHeaderParser[Date](raw.value) {
      def entry = rule {
        HttpDate ~ EOL ~> (Date(_))
      }
    }.parse.toOption
  }
}

final case class Date(date: DateTime) extends Header.Parsed {
  def key = `Date`
  override def value = date.toRfc1123DateTimeString
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

