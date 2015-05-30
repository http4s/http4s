package org.http4s
package headers

import org.http4s.Header.Raw

import scalaz.NonEmptyList

object Accept extends HeaderKey.Internal[Accept] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[Accept.HeaderT] =
    parser.AcceptHeader.ACCEPT(raw.value).toOption
}

final case class Accept(values: NonEmptyList[MediaRange]) extends Header.RecurringRenderable {
  def key = Accept
  type Value = MediaRange
}

