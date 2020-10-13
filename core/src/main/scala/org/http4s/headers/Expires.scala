/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderer, Writer}

object Expires extends HeaderKey.Internal[Expires] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[Expires] =
    HttpHeaderParser.EXPIRES(s)
}

/** A Response header that _gives the date/time after which the response is considered stale_.
  *
  * The HTTP RFCs indicate that Expires should be in the range of now to 1 year in the future.
  * However, it is a usual practice to set it to the past of far in the future
  * Thus any instant is in practice allowed
  *
  * [[https://tools.ietf.org/html/rfc7234#section-5.3 RFC-7234 Section 5.3]]
  *
  * @param expirationDate the date of expiration. The RFC has a warning, that using large values
  * can cause problems due to integer or clock overflows.
  */
final case class Expires(expirationDate: HttpDate) extends Header.Parsed {
  val key = `Expires`
  override val value = Renderer.renderString(expirationDate)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
