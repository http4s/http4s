package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Host extends HeaderKey.Internal[Host] with HeaderKey.Singleton {
  @deprecated("Use primary constructor. This will throw on invalid host or port.", "0.16.0-M5")
  def apply(host: String, port: Int): Host = apply(host, Some(port))

  @deprecated("Use primary constructor. This will throw on invalid host or port.", "0.16.0-M5")
  def apply(host: String, port: Option[Int]): Host =
    apply(
      Uri.Host.parse(host).valueOr(throw _),
      port.map(p => Uri.Port.fromInt(p).valueOr(throw _)))

  override def parse(s: String): ParseResult[Host] =
    HttpHeaderParser.HOST(s)
}

final case class Host(host: Uri.Host, port: Option[Uri.Port] = None) extends Header.Parsed {
  def key: Host.type = Host
  def renderValue(writer: Writer): writer.type = {
    writer.append(host)
    if (port.isDefined) writer << ':' << port.get
    writer
  }
}
