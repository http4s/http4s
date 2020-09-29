/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/Rfc2616BasicRules.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s.parser

import cats.implicits._
import com.github.ghik.silencer.silent
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

  @silent("deprecated")
  def Text = rule(!CTL ~ ANY | LWS)

  def Hex = rule("A" - "F" | "a" - "f" | Digit)

  def Separator = rule(anyOf("()<>@,;:\\\"/[]?={} \t"))

  @silent("deprecated")
  def Token: Rule1[String] = rule(capture(oneOrMore(!CTL ~ !Separator ~ ANY)))

  // TODO What's the replacement for DROP?
  def Comment: Rule0 = rule("(" ~ zeroOrMore(CText | QuotedPair ~> DROP | Comment) ~ ")")

  def DROP: Any => Unit = { _ =>
    ()
  }

  @silent("deprecated")
  def CText = rule(!anyOf("()") ~ Text)

  // TODO This parser cannot handle strings terminating on \" which is a border case but still valid quoted pair
  def QuotedString: Rule1[String] =
    rule {
      "\"" ~ zeroOrMore(QuotedPair | QDText) ~> { (chars: collection.Seq[Char]) =>
        new String(chars.toArray[scala.Char])
      } ~ "\""
    }

  @silent("deprecated")
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
      def Main = rule(Token ~ EOI)
    }.Main
      .run()(Parser.DeliveryScheme.Either)
      .leftMap(e => ParseFailure("Invalid token", e.format(in)))

  def isToken(in: ParserInput) = token(in).isRight
}
