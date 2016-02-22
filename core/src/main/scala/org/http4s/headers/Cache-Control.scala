package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.NonEmptyList

object `Cache-Control` extends HeaderKey.Internal[`Cache-Control`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`Cache-Control`] =
    HttpHeaderParser.CACHE_CONTROL(s)
}

final case class `Cache-Control`(values: NonEmptyList[CacheDirective]) extends Header.RecurringRenderable {
  override def key = `Cache-Control`
  type Value = CacheDirective
}

