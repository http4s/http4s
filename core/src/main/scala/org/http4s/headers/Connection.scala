package org.http4s
package headers

import org.http4s.util.{Writer, CaseInsensitiveString}
import org.http4s.util.string._

import org.http4s.util.NonEmptyList

// values should be case insensitive
//http://stackoverflow.com/questions/10953635/are-the-http-connection-header-values-case-sensitive
object Connection extends HeaderKey.Internal[Connection] with HeaderKey.Recurring

final case class Connection(values: NonEmptyList[CaseInsensitiveString]) extends Header.Recurring {
  override def key = Connection
  type Value = CaseInsensitiveString
  def hasClose = values.contains("close".ci)
  def hasKeepAlive = values.contains("keep-alive".ci)
  override def renderValue(writer: Writer): writer.type = writer.addStringNel(values.map(_.toString), ", ")
}

