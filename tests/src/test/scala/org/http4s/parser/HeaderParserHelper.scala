package org.http4s
package parser

import org.http4s.{ParseResult, Header}
import scalaz.Validation

trait HeaderParserHelper[H <: Header] {

  def hparse(value: String): ParseResult[H]

  // Also checks to make sure whitespace doesn't effect the outcome
  protected def parse(value: FieldValue): H =
    parseString(value.toString)

  // Also checks to make sure whitespace doesn't effect the outcome
  protected def parseString(value: String): H = {
    val a = hparse(value).fold(err => sys.error(s"Couldn't parse: '$value'.\nError: ${err}"), identity)
    val b = hparse(value.replace(" ", "")).fold(err => sys.error(s"Couldn't parse: $value"), identity)
    assert(a == b, "Whitespace resulted in different headers")
    a
  }
}
