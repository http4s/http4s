package org.http4s

import org.parboiled2.Parser.DeliveryScheme
import scalaz.{Failure, Success, Validation}
import org.parboiled2.ParseError
import shapeless.{HNil, ::}

package object parser {

  private[http4s] def validationScheme[H] = new DeliveryScheme[H::HNil] {
    type Result = Validation[ParseErrorInfo, H]

    def success(result: H::HNil) = Success(result.head)

    def parseError(error: ParseError) = Failure(new ParseErrorInfo(detail = error.traces.mkString("\n")))

    def failure(error: Throwable) = Failure(new ParseErrorInfo("Exception raised during parsing", error.getMessage))
  }
}
