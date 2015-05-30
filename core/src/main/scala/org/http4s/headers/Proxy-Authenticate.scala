package org.http4s
package headers

import org.http4s.Header.Raw

import scalaz.NonEmptyList

object `Proxy-Authenticate` extends HeaderKey.Internal[`Proxy-Authenticate`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`Proxy-Authenticate`.HeaderT] =
    parser.ProxyAuthenticateHeader.PROXY_AUTHENTICATE(raw.value).toOption
}

final case class `Proxy-Authenticate`(values: NonEmptyList[Challenge]) extends Header.RecurringRenderable {
  override def key = `Proxy-Authenticate`
  type Value = Challenge
}

