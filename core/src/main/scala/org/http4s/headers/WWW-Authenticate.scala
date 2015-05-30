package org.http4s
package headers

import org.http4s.Header.Raw

import scalaz.NonEmptyList

object `WWW-Authenticate` extends HeaderKey.Internal[`WWW-Authenticate`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`WWW-Authenticate`.HeaderT] =
    parser.WwwAuthenticateHeader.WWW_AUTHENTICATE(raw.value).toOption
}

final case class `WWW-Authenticate`(values: NonEmptyList[Challenge]) extends Header.RecurringRenderable {
  override def key = `WWW-Authenticate`
  type Value = Challenge
}

