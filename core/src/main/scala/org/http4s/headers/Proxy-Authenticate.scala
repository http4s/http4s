package org.http4s
package headers

import scalaz.NonEmptyList

object `Proxy-Authenticate` extends HeaderKey.Internal[`Proxy-Authenticate`] with HeaderKey.Recurring

final case class `Proxy-Authenticate`(values: NonEmptyList[Challenge]) extends RecurringRenderableHeader {
  override def key = `Proxy-Authenticate`
  type Value = Challenge
}

