package org.http4s.parser

import org.parboiled2._
import org.http4s.{Header, Cookie}
import org.joda.time.DateTime
import org.http4s.Header.`Set-Cookie`
import shapeless.{HNil, ::}

/**
 * @author Bryce Anderson
 *         Created on 2/9/14
 */
trait CookieHeader {

  def SET_COOKIE(value: String) = new SetCookieParser(value).parse

  def COOKIE(value: String) = new CookieParser(value).parse

  private class SetCookieParser(input: ParserInput) extends BaseCookieParser[Header.`Set-Cookie`](input) {
    def entry: Rule1[`Set-Cookie`] = rule {
      CookiePair ~ zeroOrMore(";" ~ OptWS ~ CookieAttrs) ~ EOI ~> (Header.`Set-Cookie`(_))
    }
  }

  private class CookieParser(input: ParserInput) extends BaseCookieParser[Header.Cookie](input) {
    def entry: Rule1[Header.Cookie] = rule {
      oneOrMore(CookiePair).separatedBy(";" ~ OptWS) ~ EOI ~> {xs: Seq[Cookie] => Header.Cookie(xs.head, xs.tail: _*)}
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
      "Expires=" ~ HttpDate ~> { (cookie: Cookie, dateTime: DateTime) => cookie.copy(expires = Some(dateTime)) } |
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
