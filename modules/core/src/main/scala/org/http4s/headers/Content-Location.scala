/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `Content-Location` extends HeaderKey.Internal[`Content-Location`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`Content-Location`] =
    HttpHeaderParser.CONTENT_LOCATION(s)
}

final case class `Content-Location`(uri: Uri) extends Header.Parsed {
  def key: `Content-Location`.type = `Content-Location`
  override def value: String = uri.toString
  def renderValue(writer: Writer): writer.type = writer << uri.toString
}
