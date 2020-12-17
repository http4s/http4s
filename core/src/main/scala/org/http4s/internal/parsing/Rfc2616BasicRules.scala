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

import cats.parse.{Numbers, Parser, Parser1, Rfc5234}

// Direct implementation of http://www.w3.org/Protocols/rfc2616/rfc2616-sec2.html#sec2
private[http4s] object Rfc2616BasicRules {
  val octet: Parser1[Char] = Parser.charIn(0x0.toChar to 0xff.toChar)
  val char: Parser1[Char] = Parser.charIn(0x0.toChar to 0x7f.toChar)
  val upAlpha: Parser1[Char] = Parser.charIn('A' to 'Z')
  val loAlpha: Parser1[Char] = Parser.charIn('a' to 'z')
  val alpha: Parser1[Char] = Parser.oneOf1(List(upAlpha, loAlpha))
  val digit: Parser1[Char] = Numbers.digit
  val alphaNum: Parser1[Char] = Parser.oneOf1(List(alpha, digit))
  val ctl: Parser1[AnyVal] = Rfc5234.ctl
  val cr: Parser1[Unit] = Rfc5234.cr
  val crlf: Parser1[Unit] = Rfc5234.crlf
  val lws: Parser1[Unit] = crlf.?.with1 *> Parser.charIn(' ', '\t').rep1.void

  val optWs: Parser[List[Unit]] = Parser.rep(lws)
  val listSep: Parser[Unit] = (optWs ~ Parser.char(',') ~ optWs).void
}
