/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.internal.parsing

import cats.syntax.all._
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

  val authParamValue: Parser[String] = token.orElse1(quotedString)
  // auth-param = token BWS "=" BWS ( token / quoted-string )
  val authParam: Parser1[(String, String)] =
    (token <* (bws ~ char('=').void ~ bws)) ~ authParamValue

  /*
    challenge = auth-scheme [ 1*SP ( token68 / [ ( "," / auth-param ) *(
    OWS "," [ OWS auth-param ] ) ] ) ]
   */
  val challenge: Parser1[Challenge] =
    ((token <* sp) ~ Parser.repSep(authParam.backtrack, 0, ows <* char(',') *> ows).map(_.toMap))
      .map { case (scheme, params) =>
        Challenge(scheme, params.getOrElse("realm", ""), params.removed("realm"))
      }

  val challenges: Parser1[NonEmptyList[Challenge]] = headerRep1(challenge)

  //auth-scheme = token
  val scheme = token.map(CaseInsensitiveString(_))

  val credentials: Parser1[Credentials] =
    ((scheme <* sp) ~ (headerRep1(authParam).backtrack.? ~ token68.?)).flatMap {
      case (scheme, (None, Some(token))) => Parser.pure(Credentials.Token(scheme, token))
      case (scheme, (Some(nel), None)) => Parser.pure(Credentials.AuthParams(scheme, nel))
      case (_, _) => Parser.fail
    }
}
