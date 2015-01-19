package org.http4s
package headers

import scalaz.NonEmptyList

object `Cache-Control` extends HeaderKey.Internal[`Cache-Control`] with HeaderKey.Recurring

final case class `Cache-Control`(values: NonEmptyList[CacheDirective]) extends Header.RecurringRenderable {
  override def key = `Cache-Control`
  type Value = CacheDirective
}

