/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.internal.parsing

import cats.parse.Parser
import cats.parse.Parser.char
import cats.parse.Parser.charIn
import cats.parse.Parser0

/** Common rules defined in RFC6265
  *
  * @see [[https://datatracker.ietf.org/doc/html/rfc6265] RFC6265, HTTP State Management Mechanism]
  */
private[http4s] abstract class Cookies(cookieOctet: Parser[Char]) {
  /* token             = <token, defined in [RFC2616], Section 2.2> */
  def token: Parser[String] = Rfc2616.token

  /* cookie-name       = token */
  def cookieName: Parser[String] = token

  /* cookie-value      = *cookie-octet */
  val cookieValue: Parser0[String] = cookieOctet.rep0.string

  /* cookie-pair       = cookie-name "=" cookie-value */
  val cookiePair: Parser[(String, String)] = (cookieName <* char('=')) ~ cookieValue

}

/* This is the strict Rfc6265 implementation. */
private[http4s] object Rfc6265
    extends Cookies(
      /* cookie-octet      = %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E
       *                   ; US-ASCII characters excluding CTLs,
       *                   ; whitespace DQUOTE, comma, semicolon,
       *                   ; and backslash
       */
      cookieOctet = charIn(
        Set(0x21.toChar) ++
          Set(0x23.toChar to 0x2b.toChar: _*) ++
          Set(0x2d.toChar to 0x3a.toChar: _*) ++
          Set(0x3c.toChar to 0x5b.toChar: _*) ++
          Set(0x5d.toChar to 0x7e.toChar: _*)
      )
    )

/** This is a relaxed implementation based on spec implemented by Chromium
  * US-ASCII characters excluding CTLs and semicolon
  * @see [[https://groups.google.com/a/chromium.org/g/chromium-discuss/c/T3oHExJMr0M]]
  */
private[http4s] object RelaxedCookies
    extends Cookies(
      cookieOctet = charIn(
        Set(0x20.toChar to 0xff.toChar: _*) - 0x3b.toChar
      )
    )
