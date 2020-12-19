/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.internal.parsing

import cats.data.NonEmptyList
import cats.parse.Parser.{char, charIn}
import cats.parse.{Parser, Parser1}
import cats.parse.Rfc5234.{alpha, digit, sp}
import org.http4s.{Challenge, Credentials}
import org.http4s.internal.parsing.Rfc7230.{bws, headerRep1, ows, quotedString, token}
import org.http4s.util.CaseInsensitiveString

private[http4s] object Rfc7235 {
  /*  token68 = 1*( ALPHA / DIGIT / "-" / "." / "_" / "~" / "+" / "/" )
   *  *"="
   * */
  val t68Chars: Parser1[Char] = charIn("-._~+/").orElse1(digit).orElse1(alpha)

  val token68: Parser1[String] = (t68Chars.rep1 ~ charIn('=').rep).string

  val authParamValue: Parser[String] = token.orElse(quotedString)
  // auth-param = token BWS "=" BWS ( token / quoted-string )
  val authParam: Parser1[(String, String)] =
    (token <* char('=').void.surroundedBy(bws)) ~ authParamValue

  //auth-scheme = token
  val scheme: Parser1[CaseInsensitiveString] = token.map(CaseInsensitiveString(_))

  /*
    challenge = auth-scheme [ 1*SP ( token68 / [ ( "," / auth-param ) *(
    OWS "," [ OWS auth-param ] ) ] ) ]
   */
  val challenge: Parser1[Challenge] =
    ((scheme <* sp) ~ Parser
      .repSep(authParam.backtrack, 0, char(',').surroundedBy(ows))
      .map(_.toMap))
      .map { case (scheme, params) =>
        //Model does not support token68 challenges
        //challenge scheme should have been CIS
        Challenge(scheme.value, params.getOrElse("realm", ""), params - "realm")
      }

  val challenges: Parser1[NonEmptyList[Challenge]] = headerRep1(challenge)

  val credentials: Parser1[Credentials] =
    ((scheme <* sp) ~ headerRep1(authParam.backtrack).map(Right(_)).orElse(token68.map(Left(_))))
      .mapFilter {
        case (scheme, Left(token)) => Some(Credentials.Token(scheme, token))
        case (scheme, Right(nel)) => Some(Credentials.AuthParams(scheme, nel))
        case (_, _) => None
      }
}
