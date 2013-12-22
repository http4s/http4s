package org.http4s.parserold

import org.http4s.parser.ParseErrorInfo

import org.parboiled.scala._
import org.parboiled.errors.{ParsingException, ParserRuntimeException, ErrorUtils}


trait Http4sParser extends Parser {

  def parse[A](rule: Rule1[A], input: String): Either[ParseErrorInfo, A] = {
    try {
      val result = ReportingParseRunner(rule).run(input)
      result.result match {
        case Some(value) => Right(value)
        case None => Left(new ParseErrorInfo(detail = ErrorUtils.printParseErrors(result)))
      }
    } catch {
      case e: ParserRuntimeException if e.getCause.isInstanceOf[ParsingException] =>
        Left(ParseErrorInfo(e.getCause.getMessage))
    }
  }

}