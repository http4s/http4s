package org.http4s
package headers

import org.http4s.util.NonEmptyList

object `WWW-Authenticate` extends HeaderKey.Internal[`WWW-Authenticate`] with HeaderKey.Recurring

final case class `WWW-Authenticate`(values: NonEmptyList[Challenge]) extends Header.RecurringRenderable {
  override def key = `WWW-Authenticate`
  type Value = Challenge
}

