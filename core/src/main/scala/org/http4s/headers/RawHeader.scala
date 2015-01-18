package org.http4s
package headers

import org.http4s.util.{Writer, CaseInsensitiveString}

/**
 * Raw representation of the Header
 *
 * This can be considered the simplest representation where the header is specified as the product of
 * a key and a value
 * @param name case-insensitive string used to identify the header
 * @param value String representation of the header value
 */
final case class RawHeader(name: CaseInsensitiveString, override val value: String) extends Header {
  override lazy val parsed = parser.HttpParser.parseHeader(this).getOrElse(this)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

