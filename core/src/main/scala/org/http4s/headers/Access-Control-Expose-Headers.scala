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

object `Access-Control-Expose-Headers`
    extends HeaderKey.Internal[`Access-Control-Expose-Headers`]
    with HeaderKey.Recurring {

  override def parse(s: String): ParseResult[`Access-Control-Expose-Headers`] =
    HttpHeaderParser.ACCESS_CONTROL_EXPOSE_HEADERS(s)

  private val ciStringRenderer: Renderer[CIString] = new Renderer[CIString] {
    override def render(writer: Writer, ciString: CIString): writer.type =
      writer << ciString
  }
}

final case class `Access-Control-Expose-Headers`(values: NonEmptyList[CIString])
    extends Header.RecurringRenderer {
  override type Value = CIString

  override implicit def renderer: Renderer[Value] = `Access-Control-Expose-Headers`.ciStringRenderer
  override def key: `Access-Control-Expose-Headers`.type = `Access-Control-Expose-Headers`
}
