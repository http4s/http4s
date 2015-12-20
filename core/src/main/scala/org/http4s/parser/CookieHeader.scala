/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/CookieHeaders.scala
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
package org.http4s
package parser

import java.time.Instant

import org.parboiled2._
import org.http4s.headers.`Set-Cookie`
import shapeless.{HNil, ::}

private[parser] trait CookieHeader {

  def SET_COOKIE(value: String) = new SetCookieParser(value).parse

  def COOKIE(value: String) = new CookieParser(value).parse

  private class SetCookieParser(input: ParserInput) extends BaseCookieParser[`Set-Cookie`](input) {
    def entry: Rule1[`Set-Cookie`] = rule {
      CookiePair ~ zeroOrMore(";" ~ OptWS ~ CookieAttrs) ~ EOI ~> (`Set-Cookie`(_))
    }
  }

  private class CookieParser(input: ParserInput) extends BaseCookieParser[headers.Cookie](input) {
    def entry: Rule1[headers.Cookie] = rule {
      oneOrMore(CookiePair).separatedBy(";" ~ OptWS) ~ EOI ~> {xs: Seq[Cookie] => headers.Cookie(xs.head, xs.tail: _*)}
    }
  }

  private abstract class BaseCookieParser[H <: Header](input: ParserInput) extends Http4sHeaderParser[H](input) {

    def CookiePair = rule {
      Token ~ ch('=') ~ CookieValue ~> (Cookie(_, _))
    }

    def CookieValue: Rule1[String] = rule {
      (('"' ~ capture(zeroOrMore(CookieOctet)) ~ "\"") | (capture(zeroOrMore(CookieOctet)))) ~ OptWS
    }

    def CookieOctet = rule {
        "\u003c" - "\u005b" |
        "\u005d" - "\u007e" |
        '\u0021' |
        "\u0023" - "\u002b" |
        "\u002d" - "\u003a"
    }

    def CookieAttrs: Rule[Cookie::HNil, Cookie::HNil] = rule {
      "Expires=" ~ HttpDate ~> { (cookie: Cookie, dateTime: Instant) => cookie.copy(expires = Some(dateTime)) } |
      "Max-Age=" ~ NonNegativeLong ~> { (cookie: Cookie, seconds: Long) => cookie.copy(maxAge = Some(seconds)) } |
      "Domain="  ~ DomainName ~> { (cookie: Cookie, domainName: String) => cookie.copy(domain = Some(domainName)) } |
      "Path="    ~ StringValue ~> { (cookie: Cookie, pathValue: String) => cookie.copy(path = Some(pathValue)) } |
      // TODO: Capture so we can create the rule, but there must be a better way
      capture("Secure")        ~> { (cookie: Cookie, s: String) => cookie.copy(secure = true) } |
      capture("HttpOnly")      ~> { (cookie: Cookie, s: String) => cookie.copy(httpOnly = true) } |
      StringValue ~> { (cookie: Cookie, stringValue: String) => cookie.copy(extension = Some(stringValue)) }
    }

    def NonNegativeLong: Rule1[Long] = rule { capture(oneOrMore(Digit)) ~> { s: String => s.toLong } }

    def DomainName: Rule1[String] = rule { capture(oneOrMore(DomainNamePart).separatedBy('.')) }

    def DomainNamePart: Rule0 = rule { AlphaNum ~ zeroOrMore(AlphaNum | ch('-')) }

    def StringValue: Rule1[String] = rule { capture(oneOrMore((!(CTL | ch(';'))) ~ Char)) }
  }

}
