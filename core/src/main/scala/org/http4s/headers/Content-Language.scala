/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import cats.data.NonEmptyList
import org.http4s.parser.HttpHeaderParser

object `Content-Language` extends HeaderKey.Internal[`Content-Language`] with HeaderKey.Recurring {
  override def parse(s: String): org.http4s.ParseResult[`Content-Language`] =
    HttpHeaderParser.CONTENT_LANGUAGE(s)
}

//RFC - https://tools.ietf.org/html/rfc3282#page-2
final case class `Content-Language`(values: NonEmptyList[LanguageTag])
    extends Header.RecurringRenderable {
  override def key: `Content-Language`.type = `Content-Language`
  type Value = LanguageTag
}
