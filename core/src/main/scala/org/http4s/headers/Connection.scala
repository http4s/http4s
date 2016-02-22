package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Writer, CaseInsensitiveString}
import org.http4s.util.string._

import scalaz.NonEmptyList

// values should be case insensitive
//http://stackoverflow.com/questions/10953635/are-the-http-connection-header-values-case-sensitive
object Connection extends HeaderKey.Internal[Connection] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[Connection] =
    HttpHeaderParser.CONNECTION(s)
}

final case class Connection(values: NonEmptyList[CaseInsensitiveString]) extends Header.Recurring {
  override def key = Connection
  type Value = CaseInsensitiveString
  def hasClose = values.list.contains("close".ci)
  def hasKeepAlive = values.list.contains("keep-alive".ci)
  override def renderValue(writer: Writer): writer.type = writer.addStrings(values.list.map(_.toString), ", ")
}

