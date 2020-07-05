/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.http4s.util.Writer
import org.http4s.parser.HttpHeaderParser

object `Access-Control-Allow-Methods`
    extends HeaderKey.Internal[`Access-Control-Allow-Methods`]
    with HeaderKey.Singleton {

  def apply(methods: Method*): `Access-Control-Allow-Methods` =
    `Access-Control-Allow-Methods`(methods.toSet)

  override def parse(s: String): ParseResult[`Access-Control-Allow-Methods`] =
    HttpHeaderParser.ACCESS_CONTROL_ALLOW_METHODS(s)
}

final case class `Access-Control-Allow-Methods`(methods: Set[Method]) extends Header.Parsed {
  override def key: `Access-Control-Allow-Methods`.type = `Access-Control-Allow-Methods`

  override def value: String =
    if (methods.isEmpty) "*"
    else methods.mkString(",")

  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
