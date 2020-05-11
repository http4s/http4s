/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/BasicRules.scala
 *
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
package org.http4s.parser

import cats.implicits._
import org.http4s.{ParseFailure, ParseResult}
import org.http4s.internal.parboiled2._

// direct implementation of http://www.w3.org/Protocols/rfc2616/rfc2616-sec2.html#sec2
private[http4s] trait Rfc2616BasicRules extends Parser {
  def Octet = rule("\u0000" - "\u00FF")

  def Char = rule("\u0000" - "\u007F")

  def Alpha = rule(LoAlpha | UpAlpha)

  def UpAlpha = rule("A" - "Z")

  def LoAlpha = rule("a" - "z")

  def Digit = rule("0" - "9")

  def AlphaNum = rule(Alpha | Digit)

  def CTL = rule("\u0000" - "\u001F" | "\u007F")

  def CRLF = rule(str("\r\n"))

  def LWS = rule(optional(CRLF) ~ oneOrMore(anyOf(" \t")))

  def Text = rule(!CTL ~ ANY | LWS)

  def Hex = rule("A" - "F" | "a" - "f" | Digit)

  def Separator = rule(anyOf("()<>@,;:\\\"/[]?={} \t"))

  def Token: Rule1[String] = rule(capture(oneOrMore(!CTL ~ !Separator ~ ANY)))

  // TODO What's the replacement for DROP?
  def Comment: Rule0 = rule("(" ~ zeroOrMore(CText | QuotedPair ~> DROP | Comment) ~ ")")

  def DROP: Any => Unit = { _ =>
    ()
  }

  def CText = rule(!anyOf("()") ~ Text)

  // TODO This parser cannot handle strings terminating on \" which is a border case but still valid quoted pair
  def QuotedString: Rule1[String] =
    rule {
      "\"" ~ zeroOrMore(QuotedPair | QDText) ~> { (chars: collection.Seq[Char]) =>
        new String(chars.toArray[scala.Char])
      } ~ "\""
    }

  def QDText: Rule1[Char] = rule(!ch('"') ~ Text ~ LASTCHAR)

  def QuotedPair: Rule1[Char] = rule("\\" ~ Char ~ LASTCHAR)

  // helpers

  def OptWS = rule(zeroOrMore(LWS))

  def ListSep = rule(oneOrMore("," ~ OptWS))

  def LASTCHAR: Rule1[Char] = rule(push(input.charAt(cursor - 1)))
}

private[http4s] object Rfc2616BasicRules {
  def token(in: ParserInput): ParseResult[String] =
    new Rfc2616BasicRules {
      override def input: ParserInput = in
    }.Token
      .run()(Parser.DeliveryScheme.Either)
      .leftMap(e => ParseFailure("Invalid token", e.format(in)))

  def isToken(in: ParserInput) = token(in).isRight
}
