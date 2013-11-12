package org.http4s
package parser

import org.parboiled.scala._
import BasicRules._

private[parser] trait AcceptRangesHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_RANGES = rule (
    RangeUnitsDef ~ EOI ~~> (Header.`Accept-Ranges`(_))
  )

  def RangeUnitsDef = rule {
    NoRangeUnitsDef | zeroOrMore(RangeUnit, separator = ListSep)
  }

  def NoRangeUnitsDef = rule {
    "none" ~ push(List.empty[RangeUnit])
  }

}