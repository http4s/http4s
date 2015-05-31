package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.Writer

object `Content-Length` extends HeaderKey.Internal[`Content-Length`] with HeaderKey.Singleton  {
  override protected def parseHeader(raw: Raw): Option[`Content-Length`] = {
    new Http4sHeaderParser[`Content-Length`](raw.value) {
      def entry = rule { Digits ~ EOL ~> {s: String => `Content-Length`(s.toInt)} }
    }.parse.toOption
  }
}

final case class `Content-Length`(length: Int) extends Header.Parsed {
  override def key = `Content-Length`
  override def renderValue(writer: Writer): writer.type = writer.append(length)
}

