/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Renderer
import cats.data.NonEmptyList

object `Accept-Patch` extends HeaderKey.Internal[`Accept-Patch`] with HeaderKey.Recurring {

  override def parse(s: String): ParseResult[`Accept-Patch`] =
    HttpHeaderParser.ACCEPT_PATCH(s)

}

// see https://tools.ietf.org/html/rfc5789#section-3.1
final case class `Accept-Patch` private (values: NonEmptyList[MediaType])
    extends Header.RecurringRenderer {

  type Value = MediaType
  val renderer = Renderer[MediaType]

  override def key: `Accept-Patch`.type = `Accept-Patch`

}
