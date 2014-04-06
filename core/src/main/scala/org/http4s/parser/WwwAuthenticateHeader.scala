package org.http4s.parser

import org.parboiled2.{Rule1, ParserInput}
import org.http4s.Header.`WWW-Authenticate`
import org.http4s.Challenge

/**
 * @author Bryce Anderson
 *         Created on 1/29/14
 */
private[parser] trait WwwAuthenticateHeader {

  def WWW_AUTHENTICATE(value: String) = new AuthenticateParser(value).parse

  private class AuthenticateParser(input: ParserInput) extends Http4sHeaderParser[`WWW-Authenticate`](input) {
    def entry: Rule1[`WWW-Authenticate`] = rule {
        oneOrMore(ChallengeRule).separatedBy(ListSep) ~ EOI ~> { xs: Seq[Challenge] =>
          `WWW-Authenticate`(xs.head, xs.tail: _*)
        }
    }

    def ChallengeRule: Rule1[Challenge] = rule {
      Token ~ oneOrMore(LWS) ~ zeroOrMore(AuthParam).separatedBy(ListSep) ~> {
        (scheme: String, params: Seq[(String, String)]) =>
          val (realms, otherParams) = params.partition(_._1 == "realm")
          Challenge(scheme, realms.headOption.map(_._2).getOrElse(""), otherParams.toMap)
      }
    }

    def AuthParam: Rule1[(String, String)] = rule {
      Token ~ "=" ~ (Token | QuotedString) ~> {(a: String, b: String) => (a,b) }
    }
  }

}
