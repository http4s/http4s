/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import cats.data.NonEmptyList
import org.http4s.parser.HttpHeaderParser

object `Proxy-Authenticate`
    extends HeaderKey.Internal[`Proxy-Authenticate`]
    with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`Proxy-Authenticate`] =
    HttpHeaderParser.PROXY_AUTHENTICATE(s)
}

/** {{{
  *   The "Proxy-Authenticate" header field consists of at least one
  *   challenge that indicates the authentication scheme(s) and parameters
  *   applicable to the proxy for this effective request URI...
  * }}}
  * From [[https://tools.ietf.org/html/rfc7235#section-4.3 RFC-7235]]
  */
final case class `Proxy-Authenticate`(values: NonEmptyList[Challenge])
    extends Header.RecurringRenderable {
  override def key: `Proxy-Authenticate`.type = `Proxy-Authenticate`
  type Value = Challenge
}
