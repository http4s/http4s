/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.internal.parsing

import cats.parse.{Parser => P, Parser1}

import Rfc5234.{alpha, digit}

/** Common rules defined in RFC7230
  *
  * @see [[https://tools.ietf.org/html/rfc7230#appendix-B]]
  */
private[http4s] object Rfc7230 {
  /* `tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
   *  "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA`
   */
  val tchar: Parser1[Char] = P.charIn("!#$%&'*+-.^_`|~").orElse1(digit).orElse1(alpha)

  /* `token = 1*tchar` */
  val token: Parser1[String] = tchar.rep1.string
}
