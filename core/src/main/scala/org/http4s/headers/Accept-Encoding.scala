package org.http4s
package headers

import scalaz.NonEmptyList

object `Accept-Encoding` extends HeaderKey.Internal[`Accept-Encoding`] with HeaderKey.Recurring

final case class `Accept-Encoding`(values: NonEmptyList[ContentCoding]) extends RecurringRenderableHeader {
  def key = `Accept-Encoding`
  type Value = ContentCoding
  def preferred: ContentCoding = values.tail.fold(values.head)((a, b) => if (a.qValue >= b.qValue) a else b)
  def satisfiedBy(coding: ContentCoding): Boolean = values.list.exists(_.satisfiedBy(coding))
}
