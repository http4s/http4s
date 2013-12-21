package org.http4s
package parserold

import org.parboiled.scala._
import BasicRules._

private[parserold] trait WwwAuthenticateHeader {
  this: Parser with AdditionalRules =>

  def WWW_AUTHENTICATE = rule {
    oneOrMore(Challenge, separator = ListSep) ~ EOI ~~> (xs => Header.`WWW-Authenticate`(xs.head, xs.tail: _*))
  }

  def Challenge = rule {
    AuthScheme ~ zeroOrMore(AuthParam, separator = ListSep) ~~> { (scheme, params) =>
      val (realms, otherParams) = params.partition(_._1 == "realm")
      org.http4s.Challenge(scheme, realms.headOption.map(_._2).getOrElse(""), otherParams.toMap)
    }
  }

}