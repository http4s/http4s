package org.http4s
package parserold

import org.parboiled.scala._
import BasicRules._
import LanguageTag._

private[parserold] trait AcceptLanguageHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_LANGUAGE = rule (
    oneOrMore(LanguageRangeDef, separator = ListSep) ~ EOI ~~> (xs => Header.`Accept-Language`(xs.head, xs.tail: _*))
  )

  def LanguageRangeDef = rule {
    (LanguageTag ~~> (org.http4s.LanguageTag(_, _: _*)) | "*" ~ push(`*`)) ~ optional(LanguageQuality)
  }

  def LanguageQuality = rule {
    ";" ~ "q" ~ "=" ~ QValue  // TODO: support language quality
  }

}