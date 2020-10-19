/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `X-B3-Sampled` extends HeaderKey.Internal[`X-B3-Sampled`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`X-B3-Sampled`] =
    HttpHeaderParser.X_B3_SAMPLED(s)
}

final case class `X-B3-Sampled`(sampled: Boolean) extends Header.Parsed {
  override def key: `X-B3-Sampled`.type = `X-B3-Sampled`

  override def renderValue(writer: Writer): writer.type = {
    val b: String = if (sampled) "1" else "0"
    writer.append(b)
  }
}
