package org.http4s
package parser

import org.parboiled.scala._
import BasicRules._
import ContentCodings._
import java.util.Locale

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
    | ContentCoding ~~> (x => getForKey(x.lowercase(Locale.ENGLISH)).getOrElse(new ContentCoding(x.lowercase(Locale.ENGLISH))))
  )

  def EncodingQuality = rule {
    ";" ~ "q" ~ "=" ~ QValue  // TODO: support encoding quality
  }

}