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

import cats.data.NonEmptyList
import org.http4s.headers.Authorization
import org.http4s.internal.parboiled2.{ParserInput, Rule0, Rule1}
import org.http4s.syntax.string._

private[parser] trait AuthorizationHeader {
  def AUTHORIZATION(value: String): ParseResult[`Authorization`] =
    new AuthorizationParser(value).parse

  // scalastyle:off public.methods.have.type
  private class AuthorizationParser(input: ParserInput)
      extends Http4sHeaderParser[Authorization](input) {
    def entry: Rule1[Authorization] = rule {
      CredentialDef ~ EOI ~> { creds: Credentials =>
        Authorization(creds)
      }
    }

    def CredentialDef = rule {
      AuthParamsCredentialsDef |
        TokenCredentialsDef
    }

    def TokenCredentialsDef = rule {
      Token ~ LWS ~ token68 ~> { (scheme: String, value: String) =>
        Credentials.Token(scheme.ci, value)
      }
    }

    def AuthParamsCredentialsDef = rule {
      Token ~ OptWS ~ CredentialParams ~> {
        (scheme: String, params: NonEmptyList[(String, String)]) =>
          Credentials.AuthParams(scheme.ci, params)
      }
    }

    def CredentialParams: Rule1[NonEmptyList[(String, String)]] = rule {
      oneOrMore(AuthParam).separatedBy(ListSep) ~> { params: Seq[(String, String)] =>
        NonEmptyList(params.head, params.tail.toList)
      }
    }

    def AuthParam: Rule1[(String, String)] = rule {
      Token ~ "=" ~ (Token | QuotedString) ~> { (s1: String, s2: String) =>
        (s1, s2)
      }
    }

    def Base64Char: Rule0 = rule { Alpha | Digit | '+' | '/' | '=' }

    // https://tools.ietf.org/html/rfc6750#page-5
    def b64token: Rule1[String] = rule {
      capture(oneOrMore(Alpha | Digit | anyOf("-._~+/")) ~ zeroOrMore('='))
    }

    def token68: Rule1[String] = b64token
  }
  // scalastyle:on public.methods.have.type
}
