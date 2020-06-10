/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `Access-Control-Allow-Credentials`
    extends HeaderKey.Internal[`Access-Control-Allow-Credentials`] {
  override def parse(s: String): ParseResult[`Access-Control-Allow-Credentials`] =
    HttpHeaderParser.ACCESS_CONTROL_ALLOW_CREDENTIALS(s)
}

// https://fetch.spec.whatwg.org/#http-access-control-allow-credentials
// This Header can only take the true value
final case class `Access-Control-Allow-Credentials`() extends Header.Parsed {
  override val value: String = "true"
  override def key: `Access-Control-Allow-Credentials`.type = `Access-Control-Allow-Credentials`
  override def renderValue(writer: Writer): writer.type =
    writer << value
}
