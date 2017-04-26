package org.http4s
package parser

import org.http4s.internal.parboiled2._
import scalaz.{\/, Validation}

private[parser] abstract class Http4sHeaderParser[H <: Header](val input: ParserInput) extends Parser with AdditionalRules  {

  def entry: Rule1[H]

  def parse: ParseResult[H] =
    entry.run()(ScalazDeliverySchemes.Disjunction)
      .leftMap(e => ParseFailure("Invalid header", e.format(s)))
}
