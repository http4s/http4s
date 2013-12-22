package org.http4s
package parser

import org.parboiled2._
import java.net.InetAddress

/**
 * @author Bryce Anderson
 *         Created on 12/22/13
 */
private[parser] trait AdditionalRules extends Rfc2616BasicRules { this: Parser =>

  def Digits: Rule1[String] = rule { capture(zeroOrMore( Digit )) }

//  def Ip: Rule1[InetAddress] = rule (
//    group(IpNumber ~ ch('.') ~ IpNumber ~ ch('.') ~ IpNumber ~ ch('.') ~ IpNumber) ~> (InetAddress.getByName(_)) ~ OptWS
//  )
//
//  def IpNumber = rule {
//    Digit ~ optional(Digit ~ optional(Digit))
//  }
//
//  def AuthScheme = rule {
//    Token ~ OptWS
//  }
//
//  def AuthParam = rule {
//    Token ~ "=" ~ (Token | QuotedString) ~~> ((_, _))
//  }
}