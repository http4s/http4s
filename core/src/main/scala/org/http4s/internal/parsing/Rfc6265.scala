/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.internal.parsing

import cats.parse.Parser.{char, charIn, rep}
import cats.parse.Rfc5234.dquote

/** Common rules defined in RFC6265
  *
  * @see [[https://tools.ietf.org/html/rfc6265] RFC6265, HTTP State Management Mechanism]
  */
private[http4s] object Rfc6265 {
  /* token             = <token, defined in [RFC2616], Section 2.2> */
  def token = Rfc2616.token

  /* cookie-name       = token */
  def cookieName = token

  /* cookie-octet      = %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E
   *                   ; US-ASCII characters excluding CTLs,
   *                   ; whitespace DQUOTE, comma, semicolon,
   *                   ; and backslash
   */
  val cookieOctet = charIn(
    Set(0x21.toChar) ++
      Set(0x23.toChar to 0x2b.toChar: _*) ++
      Set(0x2d.toChar to 0x3a.toChar: _*) ++
      Set(0x3c.toChar to 0x5b.toChar: _*) ++
      Set(0x5d.toChar to 0x7e.toChar: _*))

  /* cookie-value      = *cookie-octet / ( DQUOTE *cookie-octet DQUOTE ) */
  val cookieValue = (dquote *> rep(cookieOctet) <* dquote).orElse(rep(cookieOctet)).string

  /* cookie-pair       = cookie-name "=" cookie-value */
  val cookiePair = (cookieName <* char('=')) ~ cookieValue

}
