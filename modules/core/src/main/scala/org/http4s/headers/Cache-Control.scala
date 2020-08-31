/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import cats.data.NonEmptyList
import org.http4s.parser.HttpHeaderParser

object `Cache-Control` extends HeaderKey.Internal[`Cache-Control`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`Cache-Control`] =
    HttpHeaderParser.CACHE_CONTROL(s)
}

final case class `Cache-Control`(values: NonEmptyList[CacheDirective])
    extends Header.RecurringRenderable {
  override def key: `Cache-Control`.type = `Cache-Control`
  type Value = CacheDirective
}
