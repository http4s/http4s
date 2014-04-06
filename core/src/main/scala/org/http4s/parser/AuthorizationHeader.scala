package org.http4s.parser

import org.parboiled2.{Rule0, Rule1, ParserInput}
import org.http4s.Header.Authorization
import org.http4s.{GenericCredentials, OAuth2BearerToken, BasicCredentials, Header}

/**
 * @author Bryce Anderson
 *         Created on 2/9/14
 */
private[parser] trait AuthorizationHeader {

  def AUTHORIZATION(value: String) = new AuthorizationParser(value).parse

  private class AuthorizationParser(input: ParserInput) extends Http4sHeaderParser[Authorization](input) {
    def entry: Rule1[Authorization] = rule {
      CredentialDef ~ EOI ~> (Header.Authorization(_))
    }

    def CredentialDef = rule {
      BasicCredentialDef | OAuth2BearerTokenDef | GenericHttpCredentialsDef
    }

    def BasicCredentialDef: Rule1[BasicCredentials] = rule {
      "Basic" ~ capture(BasicCookie) ~> {s: String => BasicCredentials(s) }
    }

    def BasicCookie: Rule0 = rule {
      oneOrMore(Base64Char) ~ optional("==" | ch('='))
    }

    def OAuth2BearerTokenDef: Rule1[OAuth2BearerToken] = rule {
      "Bearer" ~ Token ~> (OAuth2BearerToken(_))
    }

    def GenericHttpCredentialsDef = rule {
      Token ~ OptWS ~ CredentialParams ~> { (scheme: String, params: Map[String, String]) =>
        GenericCredentials(org.http4s.AuthScheme.getOrCreate(scheme), params) }
    }

    def CredentialParams: Rule1[Map[String, String]] = rule {
      oneOrMore(AuthParam).separatedBy(ListSep) ~> (_.toMap) |
        (Token | QuotedString) ~> (param => Map("" -> param)) |
        push(Map.empty[String, String])
    }

    def AuthParam: Rule1[(String, String)] = rule {
      Token ~ "=" ~ (Token | QuotedString) ~> { (s1: String, s2: String) => (s1, s2) }
    }

    def Base64Char: Rule0 = rule { Alpha | Digit | '+' | '/' | '=' }
  }

}