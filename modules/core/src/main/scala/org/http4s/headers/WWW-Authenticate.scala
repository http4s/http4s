/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import cats.data.NonEmptyList
import org.http4s.parser.HttpHeaderParser

object `WWW-Authenticate` extends HeaderKey.Internal[`WWW-Authenticate`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`WWW-Authenticate`] =
    HttpHeaderParser.WWW_AUTHENTICATE(s)
}

final case class `WWW-Authenticate`(values: NonEmptyList[Challenge])
    extends Header.RecurringRenderable {
  override def key: `WWW-Authenticate`.type = `WWW-Authenticate`
  type Value = Challenge
}
