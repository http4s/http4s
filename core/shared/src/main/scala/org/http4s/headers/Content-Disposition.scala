package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `Content-Disposition`
    extends HeaderKey.Internal[`Content-Disposition`]
    with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`Content-Disposition`] =
    HttpHeaderParser.CONTENT_DISPOSITION(s)
}

// see http://tools.ietf.org/html/rfc2183
final case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String])
    extends Header.Parsed {
  override def key: `Content-Disposition`.type = `Content-Disposition`
  override lazy val value = super.value
  override def renderValue(writer: Writer): writer.type = {
    writer.append(dispositionType)
    parameters.foreach(p => writer << "; " << p._1 << "=\"" << p._2 << '"')
    writer
  }
}
