package org.http4s
package parser

import org.parboiled.scala._
import BasicRules._

private[parser] trait WwwAuthenticateHeader {
  this: Parser with AdditionalRules =>

  def WWW_AUTHENTICATE = rule {
    oneOrMore(Challenge, separator = ListSep) ~ EOI ~~> (HttpHeaders.WWWAuthenticate(_))
  }

  def Challenge = rule {
    AuthScheme ~ zeroOrMore(AuthParam, separator = ListSep) ~~> { (scheme, params) =>
      val (realms, otherParams) = params.partition(_._1 == "realm")
      HttpChallenge(scheme, realms.headOption.map(_._2).getOrElse(""), otherParams.toMap)
    }
  }

}