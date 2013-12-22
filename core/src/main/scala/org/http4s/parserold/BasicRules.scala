package org.http4s
package parserold

/*
 * Copyright (C) 2011-2012 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.parboiled2._//.scala._

// direct implementation of http://www.w3.org/Protocols/rfc2616/rfc2616-sec2.html#sec2
private[parserold] object BasicRules extends Parser {

  def Octet = rule { "\u0000" - "\u00FF" }

  def Char = rule { "\u0000" - "\u007F" }

  def Alpha = rule { LoAlpha | UpAlpha }

  def UpAlpha = rule { "A" - "Z" }

  def LoAlpha = rule { "a" - "z" }

  def Digit = rule { "0" - "9" }

  def AlphaNum = rule { Alpha | Digit }

  def CTL = rule { "\u0000" - "\u001F" | "\u001F" }

  def CRLF = rule { str("\r\n") }

  def LWS = rule { optional(CRLF) ~ oneOrMore(anyOf(" \t")) }

  def Text = rule { !CTL ~ ANY | LWS }

  def Hex = rule { "A" - "F" | "a" - "f" | Digit }

  def Separator = rule { anyOf("()<>@,;:\\\"/[]?={} \t") }

  def Token: Rule1[String] = rule { oneOrMore(!CTL ~ !Separator ~ ANY) ~> identity }

  def Comment: Rule0 = rule { "(" ~ zeroOrMore(CText | QuotedPair ~ DROP | Comment) ~ ")" }

  def CText = rule { !anyOf("()") ~ Text }

  def QuotedString: Rule1[String] = rule {
    "\"" ~ zeroOrMore(QuotedPair | QDText) ~~> (chars => new String(chars.toArray)) ~ "\""
  }

  def QDText: Rule1[Char] = rule { !ch('"') ~ Text ~:> identity }

  def QuotedPair: Rule1[Char] = rule { "\\" ~ Char ~:> identity }

  // helpers

  def OptWS = rule { zeroOrMore(LWS) }

  def ListSep = rule { oneOrMore("," ~ OptWS) }

  // we don't match scoped IPv6 addresses
  def IPv6Address = rule { oneOrMore(Hex | anyOf(":.")) }

  def IPv6Reference: Rule1[String] = rule { group("[" ~ IPv6Address ~ "]") ~> identity }
}
