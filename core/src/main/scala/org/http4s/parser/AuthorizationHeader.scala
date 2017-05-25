/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AuthorizationHeader.scala
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

import org.http4s.internal.parboiled2.{Rule0, Rule1, ParserInput}
import org.http4s.headers.{ Authorization, `Proxy-Authorization` }
import org.http4s.syntax.string._

private[parser] trait AuthorizationHeader {
  def AUTHORIZATION(value: String): ParseResult[`Authorization`] =
    new AuthorizationParser[Authorization](value) {
      def entry: Rule1[Authorization] = rule {
        CredentialDef ~ EOI ~> (Authorization(_))
      }
    }.parse

  def PROXY_AUTHORIZATION(value: String): ParseResult[`Proxy-Authorization`] =
    new AuthorizationParser[`Proxy-Authorization`](value) {
      def entry: Rule1[`Proxy-Authorization`] = rule {
        CredentialDef ~ EOI ~> (`Proxy-Authorization`(_))
      }
    }.parse

  // scalastyle:off public.methods.have.type
  private abstract class AuthorizationParser[T <: Header](input: ParserInput)
      extends Http4sHeaderParser[T](input) {

    def CredentialDef = rule {
      BasicCredentialDef | OAuth2BearerTokenDef | GenericHttpCredentialsDef
    }

    def BasicCredentialDef: Rule1[BasicCredentials] = rule {
      "Basic" ~ oneOrMore(LWS) ~ capture(BasicCookie) ~> {s: String => BasicCredentials(s) }
    }

    def BasicCookie: Rule0 = rule {
      oneOrMore(Base64Char) ~ optional("==" | ch('='))
    }

    def OAuth2BearerTokenDef: Rule1[OAuth2BearerToken] = rule {
      "Bearer" ~ oneOrMore(LWS) ~ b64token ~> (OAuth2BearerToken(_))
    }

    def GenericHttpCredentialsDef = rule {
      Token ~ OptWS ~ CredentialParams ~> { (scheme: String, params: Map[String, String]) =>
        GenericCredentials(scheme.ci, params) }
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

    // https://tools.ietf.org/html/rfc6750#page-5
    def b64token: Rule1[String] = rule {
      capture(oneOrMore(Alpha | Digit | anyOf("-._~+/")) ~ zeroOrMore('=') )
    }
  }
  // scalastyle:on public.methods.have.type
}
