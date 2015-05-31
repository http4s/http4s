package org.http4s
package parser

import org.parboiled2._
import scalaz.{\/, Validation}


private[http4s] abstract class Http4sHeaderParser[H <: Header](val input: ParserInput) extends Parser with AdditionalRules  {

  def entry: Rule1[H]

  def parse: ParseResult[H] = entry.run()(ScalazDeliverySchemes.Disjunction)
}
