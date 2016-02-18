package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser

import scalaz.NonEmptyList

object `WWW-Authenticate` extends HeaderKey.Internal[`WWW-Authenticate`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`WWW-Authenticate`] =
    HttpHeaderParser.WWW_AUTHENTICATE(s)
}

final case class `WWW-Authenticate`(values: NonEmptyList[Challenge]) extends Header.RecurringRenderable {
  override def key = `WWW-Authenticate`
  type Value = Challenge
}

