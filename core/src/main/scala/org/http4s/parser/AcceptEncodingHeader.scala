package org.http4s
package parser

import org.parboiled.scala._
import BasicRules._
import ContentCodings._

private[parser] trait AcceptEncodingHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_ENCODING = rule (
    oneOrMore(EncodingRangeDecl, separator = ListSep) ~ EOI ~~> (xs => Headers.`Accept-Encoding`(xs.head, xs.tail: _*))
  )

  def EncodingRangeDecl = rule (
    EncodingRangeDef ~ optional(EncodingQuality)
  )

  def EncodingRangeDef = rule (
      "*" ~ push(`*`)
    | ContentCoding ~~> (x => getForKey(x.lowercaseEn).getOrElse(org.http4s.ContentCoding(x.lowercaseEn)))
  )

  def EncodingQuality = rule {
    ";" ~ "q" ~ "=" ~ QValue  // TODO: support encoding quality
  }

}