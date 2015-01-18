package org.http4s
package headers

import scalaz.NonEmptyList

object Accept extends HeaderKey.Internal[Accept] with HeaderKey.Recurring

final case class Accept(values: NonEmptyList[MediaRange]) extends RecurringRenderableHeader {
  def key = Accept
  type Value = MediaRange
}

