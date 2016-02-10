package org.http4s
package headers

import org.http4s.util.NonEmptyList

object Accept extends HeaderKey.Internal[Accept] with HeaderKey.Recurring

final case class Accept(values: NonEmptyList[MediaRange]) extends Header.RecurringRenderable {
  def key = Accept
  type Value = MediaRange
}

