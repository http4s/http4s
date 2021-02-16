/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package headers

import cats.data.NonEmptyList
import cats.syntax.all._
import org.http4s.internal.parsing.{Rfc2616, Rfc7230}
import org.http4s.util.Writer
import org.typelevel.ci.CIString

// values should be case insensitive
//http://stackoverflow.com/questions/10953635/are-the-http-connection-header-values-case-sensitive
object Connection extends HeaderKey.Internal[Connection] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[Connection] =
    ParseResult.fromParser(parser, "Invalid Connection header")(s)

  private[http4s] val parser = Rfc7230.headerRep1(Rfc2616.token).map { (xs: NonEmptyList[String]) =>
    Connection(CIString(xs.head), xs.tail.map(CIString(_)): _*)
  }
}

final case class Connection(values: NonEmptyList[CIString]) extends Header.Recurring {
  override def key: Connection.type = Connection
  type Value = CIString
  def hasClose: Boolean = values.contains_(CIString("close"))
  def hasKeepAlive: Boolean = values.contains_(CIString("keep-alive"))
  override def renderValue(writer: Writer): writer.type = {
    writer.append(values.head.toString)
    values.tail.foreach(s => writer.append(", ").append(s.toString))
    writer
  }
}
