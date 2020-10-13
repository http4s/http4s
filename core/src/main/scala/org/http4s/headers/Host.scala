/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Host extends HeaderKey.Internal[Host] with HeaderKey.Singleton {
  def apply(host: String, port: Int): Host = apply(host, Some(port))

  override def parse(s: String): ParseResult[Host] =
    HttpHeaderParser.HOST(s)
}

/** A Request header, that provides the host and port informatio
  * {{{
  *   The "Host" header field in a request provides the host and port
  *   information from the target URI, enabling the origin server to
  *   distinguish among resources while servicing requests for multiple
  *   host names on a single IP address.
  * }}}
  *
  * This header was mandatory in version 1.1 of the Http protocol.
  *
  * [[https://tools.ietf.org/html/rfc7230#section-5.4 RFC-7230 Section 5.4]]
  */
final case class Host(host: String, port: Option[Int] = None) extends Header.Parsed {
  def key: Host.type = Host
  def renderValue(writer: Writer): writer.type = {
    writer.append(host)
    if (port.isDefined) writer << ':' << port.get
    writer
  }
}
