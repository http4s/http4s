package org.http4s
package parser

import org.parboiled.scala._
import BasicRules._

private[parser] trait AcceptHeader {
  this: Parser with ProtocolParameterRules with CommonActions =>

  def ACCEPT = rule (
    oneOrMore(MediaRangeDecl ~ optional(AcceptParams), separator = ListSep) ~ EOI ~~> (xs => Header.Accept(xs.head, xs.tail: _*))
  )

  def MediaRangeDecl = rule {
    MediaRangeDef ~ zeroOrMore(";" ~ Parameter ~ DROP) // TODO: support parameters
  }

  def MediaRangeDef = rule (
    ("*/*" ~ push("*", "*") | Type ~ "/" ~ ("*" ~ push("*") | Subtype) | "*" ~ push("*", "*"))
      ~~> (getMediaRange(_, _))
  )

  def AcceptParams = rule {
    ";" ~ "q" ~ "=" ~ QValue ~ zeroOrMore(AcceptExtension) // TODO: support qvalues
  }

  def AcceptExtension = rule {
    ";" ~ Token ~ optional("=" ~ (Token | QuotedString)) ~ DROP2 // TODO: support extensions
  }

  // helpers

  def getMediaRange(mainType: String, subType: String): MediaRange = {
    if (subType == "*") {
      val mainTypeLower = mainType.toLowerCase
      MediaRange.resolve(mainTypeLower)
    } else {
      getMediaType(mainType, subType)
    }
  }

}