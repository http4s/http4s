/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/WwwAuthenticateHeader.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s
package parser

import org.http4s.headers.`WWW-Authenticate`
import org.http4s.internal.parboiled2.{ParserInput, Rule1}

private[parser] trait WwwAuthenticateHeader {
  def WWW_AUTHENTICATE(value: String): ParseResult[`WWW-Authenticate`] =
    new WWWAuthenticateParser(value).parse

  private class WWWAuthenticateParser(input: ParserInput)
      extends ChallengeParser[`WWW-Authenticate`](input) {
    def entry: Rule1[`WWW-Authenticate`] = rule {
      oneOrMore(ChallengeRule).separatedBy(ListSep) ~ EOI ~> { (xs: Seq[Challenge]) =>
        `WWW-Authenticate`(xs.head, xs.tail: _*)
      }
    }
  }
}
