package org.http4s.parser

import org.parboiled.scala._
import org.parboiled.errors.{ParsingException, ParserRuntimeException, ErrorUtils}
import spray.http.RequestErrorInfo

trait Http4sParser extends Parser {

  def parse[A](rule: Rule1[A], input: String): Either[RequestErrorInfo, A] = {
    try {
      val result = ReportingParseRunner(rule).run(input)
      result.result match {
        case Some(value) => Right(value)
        case None => Left(RequestErrorInfo(detail = ErrorUtils.printParseErrors(result)))
      }
    } catch {
      case e: ParserRuntimeException if e.getCause.isInstanceOf[ParsingException] =>
        Left(RequestErrorInfo(e.getCause.getMessage))
    }
  }

}