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
import org.http4s.internal.parsing.CommonRules
import org.http4s.internal.parsing.Rfc2616
import org.typelevel.ci._

/** @see [[https://datatracker.ietf.org/doc/html/rfc6455#section-11.3.4 RFC 6455, Section 11.3.4, Sec-WebSocket-Protocol]]
  */
final case class `Sec-WebSocket-Protocol`(values: NonEmptyList[String])

object `Sec-WebSocket-Protocol` {
  def apply(head: String, tail: String*): `Sec-WebSocket-Protocol` =
    `Sec-WebSocket-Protocol`(NonEmptyList(head, tail.toList))

  def parse(s: String): ParseResult[`Sec-WebSocket-Protocol`] =
    ParseResult.fromParser(parser, "Invalid Sec-WebSocket-Protocol header")(s)

  private[http4s] val parser =
    CommonRules.headerRep1(Rfc2616.token).map(`Sec-WebSocket-Protocol`(_))

  implicit val headerInstance: Header[`Sec-WebSocket-Protocol`, Header.Recurring] =
    Header.createRendered(
      ci"Sec-WebSocket-Protocol",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[`Sec-WebSocket-Protocol`] =
    (a, b) => `Sec-WebSocket-Protocol`(a.values.concatNel(b.values))
}
