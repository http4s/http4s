/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.internal.parsing

import cats.parse.{Parser => P, Parser1}

/** Common rules defined in RFC5234 and imported into various HTTP
  * specs.
  *
  * @see https://tools.ietf.org/html/rfc5234#appendix-B.1
  */
private[http4s] object Rfc5234 {
  val alpha: P[Char] =
    P.charIn(0x41.toChar to 0x5a.toChar).orElse1(P.charIn(0x61.toChar to 0x7a.toChar))
  val cr: Parser1[Unit] = P.char(0x0d)
  val lf: Parser1[Unit] = P.char(0x0a)
  val crlf: Parser1[Unit] = (cr ~ lf).void
  val ctl: Parser1[Char] =
    P.charIn(0x00.toChar to 0x1f.toChar).orElse1(P.char(0x7f).as(0x7f.toChar))
  val digit: Parser1[Char] = P.charIn(0x30.toChar to 0x39.toChar)
  val dquote: Parser1[Unit] = P.char(0x22)
  val hexdig: Parser1[Char] = digit.orElse1(P.ignoreCaseCharIn('A', 'B', 'C', 'D', 'E', 'F'))
  val htab: Parser1[Unit] = P.char(0x09)
  val octet: Parser1[Char] = P.charIn(0x00.toChar to 0xff.toChar)
  val sp: Parser1[Unit] = P.char(0x20)
  val vchar: Parser1[Char] = P.charIn(0x21.toChar to 0x7e.toChar)
}
