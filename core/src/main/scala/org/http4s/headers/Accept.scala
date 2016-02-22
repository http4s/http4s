package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.NonEmptyList

object Accept extends HeaderKey.Internal[Accept] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[Accept] =
    HttpHeaderParser.ACCEPT(s)
}

final case class Accept(values: NonEmptyList[MediaRange]) extends Header.RecurringRenderable {
  def key = Accept
  type Value = MediaRange
}

