package org.http4s
package parser

import org.parboiled2._
import scalaz.Validation


private[parser] abstract class Http4sHeaderParser[H <: Header](val input: ParserInput) extends Parser with AdditionalRules  {

  def entry: Rule1[H]

  def parse: Validation[ParseErrorInfo, H] = entry.run()(validationScheme)
}
