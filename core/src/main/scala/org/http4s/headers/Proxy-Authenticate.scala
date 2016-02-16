package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser

import scalaz.NonEmptyList

object `Proxy-Authenticate` extends HeaderKey.Internal[`Proxy-Authenticate`] with HeaderKey.Recurring {
  override def fromString(s: String): ParseResult[`Proxy-Authenticate`] =
    HttpHeaderParser.PROXY_AUTHENTICATE(s)
}

final case class `Proxy-Authenticate`(values: NonEmptyList[Challenge]) extends Header.RecurringRenderable {
  override def key = `Proxy-Authenticate`
  type Value = Challenge
}

