package org.http4s
package headers

import org.http4s.Header.Raw

import scalaz.NonEmptyList

object `Cache-Control` extends HeaderKey.Internal[`Cache-Control`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`Cache-Control`.HeaderT] =
    parser.CacheControlHeader.CACHE_CONTROL(raw.value).toOption
}

final case class `Cache-Control`(values: NonEmptyList[CacheDirective]) extends Header.RecurringRenderable {
  override def key = `Cache-Control`
  type Value = CacheDirective
}

