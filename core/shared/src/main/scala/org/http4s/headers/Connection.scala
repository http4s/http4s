package org.http4s
package headers

import cats.data.NonEmptyList
import cats.implicits._
import org.http4s.parser.HttpHeaderParser
import org.http4s.syntax.all._
import org.http4s.util.{CaseInsensitiveString, Writer}

// values should be case insensitive
//http://stackoverflow.com/questions/10953635/are-the-http-connection-header-values-case-sensitive
object Connection extends HeaderKey.Internal[Connection] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[Connection] =
    HttpHeaderParser.CONNECTION(s)
}

final case class Connection(values: NonEmptyList[CaseInsensitiveString]) extends Header.Recurring {
  override def key: Connection.type = Connection
  type Value = CaseInsensitiveString
  def hasClose: Boolean = values.contains_("close".ci)
  def hasKeepAlive: Boolean = values.contains_("keep-alive".ci)
  override def renderValue(writer: Writer): writer.type =
    writer.addStringNel(values.map(_.toString), ", ")
}
