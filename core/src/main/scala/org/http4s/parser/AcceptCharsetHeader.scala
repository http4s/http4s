/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AcceptCharsetHeader.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s
package parser

import cats.parse.Parser1
import cats.syntax.all._
import org.http4s.CharsetRange._
import org.http4s.headers.`Accept-Charset`

private[parser] trait AcceptCharsetHeader {
  def ACCEPT_CHARSET(value: String): ParseResult[`Accept-Charset`] =
    acceptCharsetParser.parseAll(value).leftMap { e =>
      ParseFailure("Invalid Accept Charset header", e.toString)
    }

  private[http4s] val acceptCharsetParser: Parser1[`Accept-Charset`] = {
    import Rfc2616BasicRules._
    import cats.parse.Parser._
    import org.http4s.internal.parsing.Rfc7230.token

    val anyCharset = (char('*') *> QValue.parser)
      .map(q => if (q != QValue.One) `*`.withQValue(q) else `*`)

    val fromToken = (token ~ QValue.parser).mapFilter { case (s, q) =>
      // TODO handle tokens that aren't charsets
      Charset
        .fromString(s)
        .toOption
        .map { c =>
          if (q != QValue.One) c.withQuality(q) else c.toRange
        }
    }

    val charsetRange = anyCharset.orElse1(fromToken)

    (rep1Sep(charsetRange, 1, listSep) <* AdditionalRules.EOL).map(xs =>
      `Accept-Charset`(xs.head, xs.tail: _*))
  }
}
