package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.Writer

object `Content-Disposition` extends HeaderKey.Internal[`Content-Disposition`] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[`Content-Disposition`] = {
    new Http4sHeaderParser[`Content-Disposition`](raw.value) {
      def entry = rule {
        Token ~ zeroOrMore(";" ~ OptWS ~ Parameter) ~ EOL ~> { (token:String, params: Seq[(String, String)]) =>
          `Content-Disposition`(token, params.toMap)}
      }
    }.parse.toOption
  }
}

// see http://tools.ietf.org/html/rfc2183
final case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String]) extends Header.Parsed {
  override def key = `Content-Disposition`
  override lazy val value = super.value
  override def renderValue(writer: Writer): writer.type = {
    writer.append(dispositionType)
    parameters.foreach(p =>  writer << "; " << p._1 << "=\"" << p._2 << '"')
    writer
  }
}

