/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `Accept-Ranges` extends HeaderKey.Internal[`Accept-Ranges`] with HeaderKey.Singleton {
  def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply((first +: more).toList)
  def bytes: `Accept-Ranges` = apply(RangeUnit.Bytes)
  def none: `Accept-Ranges` = apply(Nil)

  override def parse(s: String): ParseResult[`Accept-Ranges`] =
    HttpHeaderParser.ACCEPT_RANGES(s)
}

final case class `Accept-Ranges` private[http4s] (rangeUnits: List[RangeUnit])
    extends Header.Parsed {
  def key: `Accept-Ranges`.type = `Accept-Ranges`
  def renderValue(writer: Writer): writer.type =
    if (rangeUnits.isEmpty) writer.append("none")
    else {
      writer.append(rangeUnits.head)
      rangeUnits.tail.foreach(r => writer.append(", ").append(r))
      writer
    }
}
