package org.http4s
package headers

import scalaz.NonEmptyList

object `WWW-Authenticate` extends HeaderKey.Internal[`WWW-Authenticate`] with HeaderKey.Recurring

final case class `WWW-Authenticate`(values: NonEmptyList[Challenge]) extends RecurringRenderableHeader {
  override def key = `WWW-Authenticate`
  type Value = Challenge
}

