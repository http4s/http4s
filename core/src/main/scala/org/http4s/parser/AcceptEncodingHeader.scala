package org.http4s
package parser

import org.parboiled.scala._
import BasicRules._
import ContentCodings._

private[parser] trait AcceptEncodingHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_ENCODING = rule (
    oneOrMore(EncodingRangeDecl, separator = ListSep) ~ EOI ~~> (Headers.AcceptEncoding(_))
  )

  def EncodingRangeDecl = rule (
    EncodingRangeDef ~ optional(EncodingQuality)
  )

  def EncodingRangeDef = rule (
      "*" ~ push(`*`)
    | ContentCoding ~~> (x => getForKey(x.toLowerCase).getOrElse(new CustomHttpContentCoding(x)))
  )

  def EncodingQuality = rule {
    ";" ~ "q" ~ "=" ~ QValue  // TODO: support encoding quality
  }

}