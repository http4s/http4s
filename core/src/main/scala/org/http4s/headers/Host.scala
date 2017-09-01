package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Host extends HeaderKey.Internal[Host] with HeaderKey.Singleton {
  def apply(host: String, port: Int): Host = apply(host, Some(port))

  override def parse(s: String): ParseResult[Host] =
    HttpHeaderParser.HOST(s)
}

final case class Host(host: String, port: Option[Int] = None) extends Header.Parsed {
  def key: Host.type = Host
  def renderValue(writer: Writer): writer.type = {
    writer.append(host)
    if (port.isDefined) writer << ':' << port.get
    writer
  }
}
