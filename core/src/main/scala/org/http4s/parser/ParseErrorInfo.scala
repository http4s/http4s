package org.http4s.parser

import scala.util.control.NoStackTrace


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