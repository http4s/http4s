package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser

import scalaz.NonEmptyList

object `Transfer-Encoding` extends HeaderKey.Internal[`Transfer-Encoding`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`Transfer-Encoding`] =
    HttpHeaderParser.TRANSFER_ENCODING(s)
}

final case class `Transfer-Encoding`(values: NonEmptyList[TransferCoding]) extends Header.RecurringRenderable {
  override def key = `Transfer-Encoding`
  def hasChunked = values.list.exists(_ == TransferCoding.chunked)
  type Value = TransferCoding
}

