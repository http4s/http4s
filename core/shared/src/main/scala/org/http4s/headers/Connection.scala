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
import org.http4s.internal.parsing.CommonRules
import org.http4s.internal.parsing.Rfc2616
import org.typelevel.ci._

// values should be case insensitive
//http://stackoverflow.com/questions/10953635/are-the-http-connection-header-values-case-sensitive
object Connection {
  def apply(head: CIString, tail: CIString*): `Connection` =
    apply(NonEmptyList(head, tail.toList))

  def parse(s: String): ParseResult[Connection] =
    ParseResult.fromParser(parser, "Invalid Connection header")(s)

  val close: Connection = Connection(ci"close")

  private[http4s] val parser =
    CommonRules.headerRep1(Rfc2616.token).map { (xs: NonEmptyList[String]) =>
      Connection(CIString(xs.head), xs.tail.map(CIString(_)): _*)
    }

  implicit val headerInstance: Header[Connection, Header.Recurring] =
    Header.createRendered(
      ci"Connection",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[Connection] =
    (a, b) => Connection(a.values.concatNel(b.values))
}

final case class Connection(values: NonEmptyList[CIString]) {
  def hasClose: Boolean = values.contains_(ci"close")
  def hasKeepAlive: Boolean = values.contains_(ci"keep-alive")
  def hasUpgrade: Boolean = values.contains_(ci"upgrade")
}
