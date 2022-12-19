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

object Trailer {
  def apply(head: CIString, tail: CIString*): Trailer =
    apply(NonEmptyList(head, tail.toList))

  def parse(s: String): ParseResult[Trailer] =
    ParseResult.fromParser(parser, "Invalid Trailer header")(s)

  private[http4s] val parser =
    CommonRules.headerRep1(Rfc2616.token).map(xs => Trailer(xs.map(CIString(_))))

  val name: CIString = ci"Trailer"

  implicit val headerInstance: Header[Trailer, Header.Recurring] =
    Header.createRendered(
      name,
      _.headers,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[Trailer] =
    (a, b) => Trailer(a.headers.concatNel(b.headers))
}

/** @see [[https://datatracker.ietf.org/doc/html/rfc7230#section-4.4, RFC 7230, Section 4.4, Trailer]]
  */
final case class Trailer(headers: NonEmptyList[CIString])
