package org.http4s
package headers

import scala.concurrent.duration.FiniteDuration
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

/**
 * Defined by http://tools.ietf.org/html/rfc6797
 */
object `Strict-Transport-Security` extends HeaderKey.Internal[`Strict-Transport-Security`] with HeaderKey.Singleton {
  def parse(s: String): ParseResult[`Strict-Transport-Security`] =
    HttpHeaderParser.STRICT_TRANSPORT_SECURITY(s)
}

case class `Strict-Transport-Security`(maxAge: FiniteDuration, includeSubDomains: Boolean = true, preload: Boolean = false) extends Header.Parsed {
  override def key: `Strict-Transport-Security`.type = `Strict-Transport-Security`
  override def renderValue(writer: Writer): writer.type = {
    writer << "max-age=" << maxAge.toSeconds
    if (includeSubDomains) writer << "; includeSubDomains"
    if (preload) writer << "; preload"
    writer
  }
}

