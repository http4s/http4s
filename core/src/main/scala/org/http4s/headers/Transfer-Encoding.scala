package org.http4s
package headers

import org.http4s.Header.Raw

import scalaz.NonEmptyList

object `Transfer-Encoding` extends HeaderKey.Internal[`Transfer-Encoding`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`Transfer-Encoding`.HeaderT] =
    parser.SimpleHeaders.TRANSFER_ENCODING(raw.value).toOption
}

final case class `Transfer-Encoding`(values: NonEmptyList[TransferCoding]) extends Header.RecurringRenderable {
  override def key = `Transfer-Encoding`
  def hasChunked = values.list.exists(_.renderString.equalsIgnoreCase("chunked"))
  type Value = TransferCoding
}

