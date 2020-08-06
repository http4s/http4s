/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.typelevel.ci.CIString
import org.http4s.parser.HttpHeaderParser
import org.http4s.util._
import cats.data.NonEmptyList

object `Access-Control-Allow-Headers`
    extends HeaderKey.Internal[`Access-Control-Allow-Headers`]
    with HeaderKey.Recurring {

  override def parse(s: String): ParseResult[`Access-Control-Allow-Headers`] =
    HttpHeaderParser.ACCESS_CONTROL_ALLOW_HEADERS(s)

  private val ciStringRenderer: Renderer[CIString] = new Renderer[CIString] {
    override def render(writer: Writer, ciString: CIString): writer.type =
      writer << ciString
  }
}

final case class `Access-Control-Allow-Headers`(values: NonEmptyList[CIString])
    extends Header.RecurringRenderer {
  override type Value = CIString

  override implicit def renderer: Renderer[Value] = `Access-Control-Allow-Headers`.ciStringRenderer
  override def key: `Access-Control-Allow-Headers`.type = `Access-Control-Allow-Headers`
}
