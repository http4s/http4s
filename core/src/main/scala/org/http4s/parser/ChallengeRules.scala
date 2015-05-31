package org.http4s.parser

import org.http4s.{Header, Challenge}
import org.parboiled2._

/** Base methods used by `Proxy-Authenticate` and `WWW-Authenticate`. */
abstract private[http4s] class ChallengeRules[H <: Header](input: ParserInput) extends Http4sHeaderParser[H](input) {

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

