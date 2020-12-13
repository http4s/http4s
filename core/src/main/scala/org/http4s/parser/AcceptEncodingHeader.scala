/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AcceptEncodingHeader.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s
package parser

import cats.parse.Parser1
import cats.syntax.either._
import org.http4s.headers.`Accept-Encoding`

private[parser] trait AcceptEncodingHeader {
  def ACCEPT_ENCODING(value: String): ParseResult[`Accept-Encoding`] =
    acceptEncodingParser.parseAll(value).leftMap { e =>
      ParseFailure("Invalid Accept Encoding header", e.toString)
    }

  private[http4s] val acceptEncodingParser: Parser1[`Accept-Encoding`] = {
    import Rfc2616BasicRules._
    import cats.parse.Parser._

    (rep1Sep(ContentCoding.parser, 1, listSep) <* AdditionalRules.EOL).map(xs =>
      `Accept-Encoding`(xs.head, xs.tail: _*))
  }
}
