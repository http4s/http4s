package org.http4s
package parser

import org.parboiled2._
import org.parboiled2.ParseError
import org.parboiled2.Parser.DeliveryScheme
import scalaz.{Validation, Success, Failure}


object ParseErrorInfo {
  def apply(message: String): ParseErrorInfo  = message.split(": ", 2) match {
    case Array(summary, detail) => apply(summary, detail)
    case _ => ParseErrorInfo("", message)
  }
}
case class ParseErrorInfo(summary: String = "", detail: String = "") {
  def withSummary(newSummary: String) = copy(summary = newSummary)
  def withFallbackSummary(fallbackSummary: String) = if (summary.isEmpty) withSummary(fallbackSummary) else this
  def formatPretty = summary + ": " + detail
}

abstract class Http4sHeaderParser[H <: Header](val input: ParserInput) extends Parser with AdditionalRules  {

  import Http4sHeaderParser.validationScheme

  def entry: Rule1[H]

  def parse: Validation[ParseErrorInfo, H] = entry.run()(validationScheme)

}

object Http4sHeaderParser {
  import shapeless.{HNil, ::}

  def validationScheme[H <: Header] = new DeliveryScheme[H::HNil] {
    type Result = Validation[ParseErrorInfo, H]

    def success(result: H::HNil) = Success(result.head)

    def parseError(error: ParseError) =
      Failure(new ParseErrorInfo(detail = error.traces.mkString("\n")))

    def failure(error: Throwable) =
      Failure(new ParseErrorInfo("Exception raised during parsing", error.getMessage))

  }

}
