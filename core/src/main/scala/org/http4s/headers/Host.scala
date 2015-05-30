package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.util.Writer

object Host extends HeaderKey.Internal[Host] with HeaderKey.Singleton {

  override protected def parseHeader(raw: Raw): Option[Host.HeaderT] =
    parser.SimpleHeaders.HOST(raw.value).toOption

  def apply(host: String, port: Int): Host = apply(host, Some(port))
}

final case class Host(host: String, port: Option[Int] = None) extends Header.Parsed {
  def key = `Host`
  def renderValue(writer: Writer): writer.type = {
    writer.append(host)
    if (port.isDefined) writer << ':' << port.get
    writer
  }
}

