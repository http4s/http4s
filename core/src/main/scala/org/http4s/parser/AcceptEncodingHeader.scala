package org.http4s
package parser

import org.parboiled.scala._
import BasicRules._
import HttpEncodings._

private[parser] trait AcceptEncodingHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_ENCODING = rule (
    oneOrMore(EncodingRangeDecl, separator = ListSep) ~ EOI ~~> (HttpHeaders.AcceptEncoding(_))
  )

  def EncodingRangeDecl = rule (
    EncodingRangeDef ~ optional(EncodingQuality)
  )

  def EncodingRangeDef = rule (
      "*" ~ push(`*`)
    | ContentCoding ~~> (x => getForKey(x.toLowerCase).getOrElse(new CustomHttpEncoding(x)))
  )

  def EncodingQuality = rule {
    ";" ~ "q" ~ "=" ~ QValue  // TODO: support encoding quality
  }

}