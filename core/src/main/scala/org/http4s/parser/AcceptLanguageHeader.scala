/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AcceptLanguageHeader.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s
package parser

import cats.parse.Parser1
import cats.syntax.either._
import org.http4s.headers.`Accept-Language`
import org.http4s.internal.parsing.Rfc7230

private[parser] trait AcceptLanguageHeader {
  def ACCEPT_LANGUAGE(value: String): ParseResult[`Accept-Language`] =
    acceptLanguageParser.parseAll(value).leftMap { e =>
      ParseFailure("Invalid Accept Language header", e.toString)
    }

  private[http4s] val acceptLanguageParser: Parser1[`Accept-Language`] = {
    import Rfc2616BasicRules._
    import cats.parse.Parser.{char => ch, _}
    import cats.parse.Rfc5234._

    val languageTag =
      (string1(alpha.rep1(1)) ~ (ch('-') *> Rfc7230.token).rep ~ QValue.parser).map {
        case ((main, sub), q) => LanguageTag(main, q, sub)
      }

    rep1Sep(languageTag, 1, listSep).map(tags => `Accept-Language`(tags.head, tags.tail: _*))
  }
}
