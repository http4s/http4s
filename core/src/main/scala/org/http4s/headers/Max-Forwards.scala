/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

// see https://tools.ietf.org/html/rfc7231#section-5.1.2
sealed abstract case class `Max-Forwards`(count: Long) extends Header.Parsed {
  override def key: `Max-Forwards`.type = `Max-Forwards`
  override def value: String = count.toString
  def renderValue(writer: Writer): writer.type = writer << value
}

object `Max-Forwards` extends HeaderKey.Internal[`Max-Forwards`] with HeaderKey.Singleton {
  private class MaxForwardsImpl(length: Long) extends `Max-Forwards`(length)

  val zero: `Max-Forwards` = new MaxForwardsImpl(0)

  def fromLong(length: Long): ParseResult[`Max-Forwards`] =
    if (length >= 0L) ParseResult.success(new MaxForwardsImpl(length))
    else ParseResult.fail("Invalid Max-Forwards", length.toString)

  def unsafeFromLong(length: Long): `Max-Forwards` =
    fromLong(length).fold(throw _, identity)

  override def parse(s: String): ParseResult[`Max-Forwards`] =
    HttpHeaderParser.MAX_FORWARDS(s)

}
