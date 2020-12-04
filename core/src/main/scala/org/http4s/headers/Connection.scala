/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import cats.data.NonEmptyList
import cats.syntax.all._
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer
import org.typelevel.ci.CIString

// values should be case insensitive
//http://stackoverflow.com/questions/10953635/are-the-http-connection-header-values-case-sensitive
object Connection extends HeaderKey.Internal[Connection] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[Connection] =
    HttpHeaderParser.CONNECTION(s)
}

final case class Connection(values: NonEmptyList[CIString]) extends Header.Recurring {
  override def key: Connection.type = Connection
  type Value = CIString
  def hasClose: Boolean = values.contains_(CIString("close"))
  def hasKeepAlive: Boolean = values.contains_(CIString("keep-alive"))
  override def renderValue(writer: Writer): writer.type =
    writer.addStringNel(values.map(_.toString), ", ")
}
