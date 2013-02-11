package org.http4s.parser

import org.parboiled.scala._
import org.parboiled.errors.{ParsingException, ParserRuntimeException, ErrorUtils}

case class ParseErrorInfo(summary: String = "", detail: String = "") {
  def withSummary(newSummary: String) = copy(summary = newSummary)
  def withFallbackSummary(fallbackSummary: String) = if (summary.isEmpty) withSummary(fallbackSummary) else this
  def formatPretty = summary + ": " + detail
}
trait Http4sParser extends Parser {

  def parse[A](rule: Rule1[A], input: String): Either[ParseErrorInfo, A] = {
    try {
      val result = ReportingParseRunner(rule).run(input)
      result.result match {
        case Some(value) => Right(value)
        case None => Left(ParseErrorInfo(detail = ErrorUtils.printParseErrors(result)))
      }
    } catch {
      case e: ParserRuntimeException if e.getCause.isInstanceOf[ParsingException] =>
        Left(ParseErrorInfo(e.getCause.getMessage))
    }
  }

}