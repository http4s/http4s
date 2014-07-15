package org.http4s.parser

import org.http4s.Header
import scalaz.Validation

trait HeaderParserHelper[H <: Header] {

  def hparse(value: String): Validation[ParseErrorInfo, H]
  // Also checks to make sure whitespace doesn't effect the outcome
  protected def parse(value: String): H = {
    val a = hparse(value).fold(err => sys.error(s"Couldn't parse: '$value'.\nError: ${err.summary}"), identity)
    val b = hparse(value.replace(" ", "")).fold(err => sys.error(s"Couldn't parse: $value"), identity)
    assert(a == b, "Whitespace resulted in different headers")
    a
  }

}
