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
import cats.parse.Parser
import cats.parse.Parser.char
import cats.parse.Parser.charIn
import cats.parse.Parser0
import cats.parse.Rfc5234.alpha
import cats.parse.Rfc5234.digit
import cats.parse.Rfc5234.sp
import org.http4s.Challenge
import org.http4s.Credentials
import org.http4s.internal.parsing.CommonRules.bws
import org.http4s.internal.parsing.CommonRules.headerRep1
import org.http4s.internal.parsing.CommonRules.ows
import org.http4s.internal.parsing.CommonRules.quotedString
import org.http4s.internal.parsing.CommonRules.token
import org.typelevel.ci.CIString

private[parsing] trait AuthRules {
  /*  token68 = 1*( ALPHA / DIGIT / "-" / "." / "_" / "~" / "+" / "/" )
   *  *"="
   * */
  val t68Chars: Parser[Char] = charIn("-._~+/").orElse(digit).orElse(alpha)

  val token68: Parser[String] = (t68Chars.rep ~ charIn('=').rep0).string

  val authParamValue: Parser0[String] = token.orElse(quotedString)
  // auth-param = token BWS "=" BWS ( token / quoted-string )
  val authParam: Parser[(String, String)] =
    (token <* char('=').void.surroundedBy(bws)) ~ authParamValue

  // auth-scheme = token
  val scheme: Parser[CIString] = token.map(CIString(_))

  /*
    challenge = auth-scheme [ 1*SP ( token68 / [ ( "," / auth-param ) *(
    OWS "," [ OWS auth-param ] ) ] ) ]
   */
  val challenge: Parser[Challenge] =
    ((scheme <* sp) ~ authParam.backtrack
      .repSep0(char(',').surroundedBy(ows))
      .map(_.toMap))
      .map { case (scheme, params) =>
        // Model does not support token68 challenges
        // challenge scheme should have been CIS
        Challenge(scheme.toString, params.getOrElse("realm", ""), params - "realm")
      }

  val challenges: Parser[NonEmptyList[Challenge]] = headerRep1(challenge)

  val credentials: Parser[Credentials] =
    ((scheme <* sp) ~ headerRep1(authParam.backtrack).map(Right(_)).orElse(token68.map(Left(_))))
      .map {
        case (scheme, Left(token)) => Credentials.Token(scheme, token)
        case (scheme, Right(nel)) => Credentials.AuthParams(scheme, nel)
      }
}

private[http4s] object AuthRules extends AuthRules
