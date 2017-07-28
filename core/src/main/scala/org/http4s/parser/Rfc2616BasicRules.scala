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

import scala.reflect.ClassTag
import scalaz.Validation
import org.http4s.{ParseFailure, ParseResult}
import org.http4s.internal.parboiled2._
import org.http4s.internal.parboiled2.CharPredicate.{ HexDigit => HEXDIG }

// direct implementation of http://www.w3.org/Protocols/rfc2616/rfc2616-sec2.html#sec2
private[http4s] trait Rfc2616BasicRules extends Parser {
  // scalastyle:off public.methods.have.type
  def Octet = rule { "\u0000" - "\u00FF" }

  def Char = rule { "\u0000" - "\u007F" }

  def Alpha = rule { LoAlpha | UpAlpha }

  def UpAlpha = rule { "A" - "Z" }

  def LoAlpha = rule { "a" - "z" }

  def Digit = rule { "0" - "9" }

  def AlphaNum = rule { Alpha | Digit }

  def CTL = rule { "\u0000" - "\u001F" | "\u007F" }

  def CRLF = rule { str("\r\n") }

  def LWS = rule { optional(CRLF) ~ oneOrMore(anyOf(" \t")) }

  def Text = rule { !CTL ~ ANY | LWS }

  def Hex = rule { "A" - "F" | "a" - "f" | Digit }

  def Separator = rule { anyOf("()<>@,;:\\\"/[]?={} \t") }

  def Token: Rule1[String] = rule { capture(oneOrMore(!CTL ~ !Separator ~ ANY)) }

  // TODO What's the replacement for DROP?
  def Comment: Rule0 = rule { "(" ~ zeroOrMore(CText | QuotedPair ~> DROP | Comment) ~ ")" }

  def DROP: Any => Unit = { _ => () }

  def CText = rule { !anyOf("()") ~ Text }

  def QuotedString: Rule1[String] = rule {
    "\"" ~ zeroOrMore(QuotedPair | QDText) ~> {chars: Seq[Char] => new String(chars.toArray[scala.Char])} ~ "\""
  }

  def QDText: Rule1[Char] = rule { !ch('"') ~ Text ~ LASTCHAR }

  def QuotedPair: Rule1[Char] = rule { "\\" ~ Char ~ LASTCHAR }

  // helpers

  def OptWS = rule { zeroOrMore(LWS) }

  def ListSep = rule { oneOrMore("," ~ OptWS) }

  def LASTCHAR: Rule1[Char] = rule { push(input.charAt(cursor - 1)) }

  private val DIGIT = CharPredicate.Digit
  private val DIGIT04 = CharPredicate('0' to '4')
  private val DIGIT05 = CharPredicate('0' to '5')
  private val DIGIT19 = CharPredicate.Digit19

  def IPNumber = rule {
    capture(
      '2' ~ (DIGIT04 ~ DIGIT | '5' ~ DIGIT05)
        | '1' ~ DIGIT ~ DIGIT
        | DIGIT19 ~ DIGIT
        | DIGIT) ~> (java.lang.Integer.parseInt(_).toByte)
  }

  def IPv4Address = {
    rule {
      IPNumber ~ '.' ~ IPNumber ~ '.' ~ IPNumber ~ '.' ~ IPNumber ~> (Array[Byte](_, _, _, _))
    }
  }

  def IPv6Address: Rule1[Array[Byte]] = {
    import CharUtils.{ hexValue ⇒ hv }
    var a: Array[Byte] = null
    def zero(ix: Int) = rule { run(a(ix) = 0.toByte) }
    def zero2(ix: Int) = rule { run { a(ix) = 0.toByte; a(ix + 1) = 0.toByte; } }
    def h4(ix: Int) = rule { HEXDIG ~ run(a(ix) = hv(lastChar).toByte) }
    def h8(ix: Int) = rule { HEXDIG ~ HEXDIG ~ run(a(ix) = (hv(charAt(-2)) * 16 + hv(lastChar)).toByte) }
    def h16(ix: Int) = rule { h8(ix) ~ h8(ix + 1) | h4(ix) ~ h8(ix + 1) | zero(ix) ~ h8(ix + 1) | zero(ix) ~ h4(ix + 1) }
    def h16c(ix: Int) = rule { h16(ix) ~ ':' ~ !':' }
    def ch16o(ix: Int) = rule { optional(':' ~ !':') ~ (h16(ix) | zero2(ix)) }
    def ls32 = rule { h16(12) ~ ':' ~ h16(14) | IPv4Address ~> (System.arraycopy(_, 0, a, 12, 4)) }
    def cc(ix: Int) = rule { ':' ~ ':' ~ zero2(ix) }
    def tail2 = rule { h16c(2) ~ tail4 }
    def tail4 = rule { h16c(4) ~ tail6 }
    def tail6 = rule { h16c(6) ~ tail8 }
    def tail8 = rule { h16c(8) ~ tail10 }
    def tail10 = rule { h16c(10) ~ ls32 }
    rule {
      !(':' ~ HEXDIG) ~ push { a = new Array[Byte](16); a } ~ (
        h16c(0) ~ tail2
        | cc(0) ~ tail2
        | ch16o(0) ~ (
          cc(2) ~ tail4
          | ch16o(2) ~ (
            cc(4) ~ tail6
            | ch16o(4) ~ (
              cc(6) ~ tail8
              | ch16o(6) ~ (
                cc(8) ~ tail10
                | ch16o(8) ~ (
                  cc(10) ~ ls32
                  | ch16o(10) ~ (
                    cc(12) ~ h16(14)
                      | ch16o(12) ~ cc(14))))))))
    }
  }

  def IPv6Reference: Rule1[String] = rule { capture("[" ~ oneOrMore(HEXDIG | anyOf(":.")) ~ "]") }
}

private[http4s] object Rfc2616BasicRules {
  def token(in: ParserInput): ParseResult[String] = new Rfc2616BasicRules {
    override def input: ParserInput = in
  }.Token.run()(ScalazDeliverySchemes.Disjunction)
    .leftMap(e => ParseFailure("Invalid token", e.format(in)))

  def isToken(in: ParserInput) = token(in).isRight
}
