/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderer, Writer}

object `Last-Modified` extends HeaderKey.Internal[`Last-Modified`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`Last-Modified`] =
    HttpHeaderParser.LAST_MODIFIED(s)
}

/** Response header that indicates the time at which the server believes the
  * entity was last modified.
  *
  * [[https://tools.ietf.org/html/rfc7232#section-2.3 RFC-7232]]
  */
final case class `Last-Modified`(date: HttpDate) extends Header.Parsed {
  override def key: `Last-Modified`.type = `Last-Modified`
  override def value: String = Renderer.renderString(date)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
