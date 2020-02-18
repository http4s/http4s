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

import org.http4s.SameSite._
import org.http4s.headers.`Set-Cookie`
import org.http4s.internal.parboiled2._
import org.http4s.internal.parboiled2.support.{::, HNil}

private[parser] trait CookieHeader {
  def SET_COOKIE(value: String): ParseResult[`Set-Cookie`] =
    new SetCookieParser(value).parse

  def COOKIE(value: String): ParseResult[headers.Cookie] =
    new CookieParser(value).parse

  // scalastyle:off public.methods.have.type
  private class SetCookieParser(input: ParserInput) extends BaseCookieParser[`Set-Cookie`](input) {
    def entry: Rule1[`Set-Cookie`] = rule {
      CookiePair(ResponseCookie(_, _)) ~ zeroOrMore(";" ~ OptWS ~ CookieAttrs) ~ EOI ~> (`Set-Cookie`(
        _))
    }
  }

  private class CookieParser(input: ParserInput) extends BaseCookieParser[headers.Cookie](input) {
    def entry: Rule1[headers.Cookie] = rule {
      oneOrMore(CookiePair(RequestCookie)).separatedBy(";" ~ OptWS) ~ optional(";") ~ EOI ~> {
        xs: Seq[RequestCookie] =>
          headers.Cookie(xs.head, xs.tail: _*)
      }
    }
  }

  private abstract class BaseCookieParser[H <: Header](input: ParserInput)
      extends Http4sHeaderParser[H](input) {
    def CookiePair[A](f: (String, String) => A) = rule {
      Token ~ ch('=') ~ CookieValue ~> (f(_, _))
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

    def CookieAttrs: Rule[ResponseCookie :: HNil, ResponseCookie :: HNil] = rule {
      ignoreCase("expires=") ~ HttpDate ~> { (cookie: ResponseCookie, dateTime: HttpDate) =>
        cookie.copy(expires = Some(dateTime))
      } |
        ignoreCase("max-age=") ~ NonNegativeLong ~> { (cookie: ResponseCookie, seconds: Long) =>
          cookie.copy(maxAge = Some(seconds))
        } |
        ignoreCase("domain=") ~ DomainName ~> { (cookie: ResponseCookie, domainName: String) =>
          cookie.copy(domain = Some(domainName))
        } |
        ignoreCase("path=") ~ StringValue ~> { (cookie: ResponseCookie, pathValue: String) =>
          cookie.copy(path = Some(pathValue))
        } |
        ignoreCase("samesite=") ~ SameSite ~> { (cookie: ResponseCookie, sameSiteValue: SameSite) =>
          cookie.copy(sameSite = sameSiteValue)
        } |
        // TODO: Capture so we can create the rule, but there must be a better way
        ignoreCase("secure") ~ MATCH ~> { (cookie: ResponseCookie) =>
          cookie.copy(secure = true)
        } |
        ignoreCase("httponly") ~ MATCH ~> { (cookie: ResponseCookie) =>
          cookie.copy(httpOnly = true)
        } |
        StringValue ~> { (cookie: ResponseCookie, stringValue: String) =>
          val ext0 = cookie.extension match {
            case Some(extension) => s"${extension}; $stringValue"
            case scala.None => stringValue
          }
          cookie.copy(extension = Some(ext0))
        }
    }

    def NonNegativeLong: Rule1[Long] = rule {
      capture(oneOrMore(Digit)) ~> { s: String =>
        s.toLong
      }
    }

    def DomainName: Rule1[String] = rule {
      capture(optional('.') ~ oneOrMore(DomainNamePart).separatedBy('.'))
    }

    def DomainNamePart: Rule0 = rule { AlphaNum ~ zeroOrMore(AlphaNum | ch('-')) }

    def StringValue: Rule1[String] = rule { capture(oneOrMore((!(CTL | ch(';'))) ~ Char)) }

    def SameSite: Rule1[SameSite] = rule {
      ignoreCase("strict") ~ push(Strict) | ignoreCase("lax") ~ push(Lax) | ignoreCase("none") ~ push(
        None)
    }
  }
  // scalastyle:on public.methods.have.type
}
