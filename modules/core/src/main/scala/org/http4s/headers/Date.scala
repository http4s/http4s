/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderer, Writer}

object Date extends HeaderKey.Internal[Date] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[Date] =
    HttpHeaderParser.DATE(s)
}

final case class Date(date: HttpDate) extends Header.Parsed {
  def key: Date.type = Date
  override def value: String = Renderer.renderString(date)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
