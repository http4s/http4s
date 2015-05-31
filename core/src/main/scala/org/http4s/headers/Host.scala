package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.Writer

object Host extends HeaderKey.Internal[Host] with HeaderKey.Singleton {

  def apply(host: String, port: Int): Host = apply(host, Some(port))

  override protected def parseHeader(raw: Raw): Option[Host] = {
    // Do not accept scoped IPv6 addresses as they should not appear in the Host header,
    // see also https://issues.apache.org/bugzilla/show_bug.cgi?id=35122 (WONTFIX in Apache 2 issue) and
    // https://bugzilla.mozilla.org/show_bug.cgi?id=464162 (FIXED in mozilla)
    new Http4sHeaderParser[Host](raw.value) {
      def entry = rule {
        (Token | IPv6Reference) ~ OptWS ~
          optional(":" ~ capture(oneOrMore(Digit)) ~> (_.toInt)) ~ EOL ~> (Host(_:String, _:Option[Int]))
      }
    }.parse.toOption
  }
}

final case class Host(host: String, port: Option[Int] = None) extends Header.Parsed {
  def key = `Host`
  def renderValue(writer: Writer): writer.type = {
    writer.append(host)
    if (port.isDefined) writer << ':' << port.get
    writer
  }
}

