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
import cats.parse.Parser
import org.http4s.Header
import org.http4s.internal.parsing.AuthRules
import org.typelevel.ci._

object `WWW-Authenticate` {

  def apply(head: Challenge, tail: Challenge*): `WWW-Authenticate` =
    apply(NonEmptyList(head, tail.toList))

  private[http4s] val parser: Parser[`WWW-Authenticate`] =
    AuthRules.challenges.map(`WWW-Authenticate`.apply)

  def parse(s: String): ParseResult[`WWW-Authenticate`] =
    ParseResult.fromParser(parser, "Invalid WWW-Authenticate header")(s)

  implicit val headerInstance: Header[`WWW-Authenticate`, Header.Recurring] =
    Header.createRendered(
      ci"WWW-Authenticate",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[`WWW-Authenticate`] =
    (a, b) => `WWW-Authenticate`(a.values.concatNel(b.values))
}

final case class `WWW-Authenticate`(values: NonEmptyList[Challenge])
