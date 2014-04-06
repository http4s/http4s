package org.http4s

import org.parboiled2.Parser.DeliveryScheme
import scalaz.{Failure, Success, Validation}
import org.parboiled2.ParseError
import shapeless.{HNil, ::}
import scala.util.control.NoStackTrace

/**
 * Created by Bryce Anderson on 4/5/14.
 */
package object parser {

  private[parser] def validationScheme[H] = new DeliveryScheme[H::HNil] {
    type Result = Validation[ParseErrorInfo, H]

    def success(result: H::HNil) = Success(result.head)

    def parseError(error: ParseError) = Failure(new ParseErrorInfo(detail = error.traces.mkString("\n")))

    def failure(error: Throwable) = Failure(new ParseErrorInfo("Exception raised during parsing", error.getMessage))
  }

  private[parser] object ParseErrorInfo {
    def apply(message: String): ParseErrorInfo  = message.split(": ", 2) match {
      case Array(summary, detail) => apply(summary, detail)
      case _ => ParseErrorInfo("", message)
    }
  }

  case class ParseErrorInfo (summary: String = "", detail: String = "") extends Exception with NoStackTrace {
    def withSummary(newSummary: String) = copy(summary = newSummary)
    def withFallbackSummary(fallbackSummary: String) = if (summary.isEmpty) withSummary(fallbackSummary) else this
    def formatPretty = summary + ": " + detail
  }
}
