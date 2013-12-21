package org.http4s

import org.http4s.parserold.ParseErrorInfo

class Http4sException(val message: String, val cause: Option[Throwable] = None)
  extends RuntimeException(message, cause.orNull)

class ParseException(val errorInfo: ParseErrorInfo) extends Http4sException(errorInfo.formatPretty)