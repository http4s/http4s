package org.http4s
package headers

import org.http4s.Header.Raw

import scalaz.NonEmptyList

object `Accept-Encoding` extends HeaderKey.Internal[`Accept-Encoding`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`Accept-Encoding`.HeaderT] =
    parser.AcceptEncodingHeader.ACCEPT_ENCODING(raw.value).toOption
}

final case class `Accept-Encoding`(values: NonEmptyList[ContentCoding]) extends Header.RecurringRenderable {
  def key = `Accept-Encoding`
  type Value = ContentCoding
  def preferred: ContentCoding = values.tail.fold(values.head)((a, b) => if (a.qValue >= b.qValue) a else b)
  def satisfiedBy(coding: ContentCoding): Boolean = values.list.exists(_.satisfiedBy(coding))
}
