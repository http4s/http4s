/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.http4s
import org.http4s.ServerSentEvent._
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

final case class `Last-Event-Id`(id: EventId) extends Header.Parsed {
  override def key: http4s.headers.`Last-Event-Id`.type = `Last-Event-Id`
  override def renderValue(writer: Writer): writer.type =
    writer.append(id.value)
}

object `Last-Event-Id` extends HeaderKey.Internal[`Last-Event-Id`] with HeaderKey.Singleton {
  def parse(s: String): ParseResult[`Last-Event-Id`] =
    HttpHeaderParser.LAST_EVENT_ID(s)
}
