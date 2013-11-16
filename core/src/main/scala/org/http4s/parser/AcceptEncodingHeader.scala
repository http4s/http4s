package org.http4s
package parser

import org.parboiled.scala._
import BasicRules._
import ContentCoding._

private[parser] trait AcceptEncodingHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_ENCODING = rule (
    oneOrMore(EncodingRangeDecl, separator = ListSep) ~ EOI ~~> (xs => Header.`Accept-Encoding`(xs.head, xs.tail: _*))
  )

  def EncodingRangeDecl = rule (
    EncodingRangeDef ~ optional(EncodingQuality)
  )

  def EncodingRangeDef = rule (
      "*" ~ push(`*`)
    | ContentCoding ~~> (x => getForKey(x.ci).getOrElse(org.http4s.ContentCoding(x.ci)))
  )

  def EncodingQuality = rule {
    ";" ~ "q" ~ "=" ~ QValue  // TODO: support encoding quality
  }

}