/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.internal.parsing

import cats.parse.Parser.charIn
import cats.parse.Parser1
import cats.parse.Rfc5234.{alpha, digit}

private[http4s] object Rfc7235 {
  /*  token68 = 1*( ALPHA / DIGIT / "-" / "." / "_" / "~" / "+" / "/" )
   *  *"="
   * */
  val t68Chars: Parser1[Char] = charIn("-._~+/").orElse1(digit).orElse1(alpha)

  val token68: Parser1[String] = (t68Chars.rep1 ~ charIn('=').rep).string
}
