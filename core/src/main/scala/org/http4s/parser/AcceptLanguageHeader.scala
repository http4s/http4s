package org.http4s
package parser

import org.parboiled.scala._
import BasicRules._
import LanguageRanges._

private[parser] trait AcceptLanguageHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_LANGUAGE = rule (
    oneOrMore(LanguageRangeDef, separator = ListSep) ~ EOI ~~> (HttpHeaders.`Accept-Language`(_))
  )

  def LanguageRangeDef = rule {
    (LanguageTag ~~> (Language(_, _: _*)) | "*" ~ push(`*`)) ~ optional(LanguageQuality)
  }

  def LanguageQuality = rule {
    ";" ~ "q" ~ "=" ~ QValue  // TODO: support language quality
  }

}