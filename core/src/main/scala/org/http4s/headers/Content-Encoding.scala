package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.Writer
import org.http4s.util.string._

object `Content-Encoding` extends HeaderKey.Internal[`Content-Encoding`] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[`Content-Encoding`] = {
    new Http4sHeaderParser[`Content-Encoding`](raw.value) {
      def entry = rule { Token ~ EOL ~> {s: String =>
        `Content-Encoding`(ContentCoding.getOrElseCreate(s.ci))}
      }
    }.parse.toOption
  }
}

final case class `Content-Encoding`(contentCoding: ContentCoding) extends Header.Parsed {
  override def key = `Content-Encoding`
  override def renderValue(writer: Writer): writer.type = contentCoding.render(writer)
}

