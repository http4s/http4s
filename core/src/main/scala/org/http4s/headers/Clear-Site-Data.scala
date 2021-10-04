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

import cats.Semigroup
import cats.data.NonEmptyList
import cats.parse.Parser
import org.typelevel.ci._
import org.http4s.Header
import org.http4s.internal.parsing.Rfc7230

/** Clear-Site-Data header
  * See https://www.w3.org/TR/clear-site-data/
  */
object `Clear-Site-Data` {
  def apply(head: SiteData, tail: SiteData*): `Clear-Site-Data` =
    apply(NonEmptyList(head, tail.toList))

  def parse(s: String): ParseResult[`Clear-Site-Data`] =
    ParseResult.fromParser(parser, "Invalid Clear-Site-Data header")(s)

  private[http4s] val parser: Parser[`Clear-Site-Data`] =
    Rfc7230.headerRep1(SiteData.parser).map(apply)

  implicit val headerInstance: Header[`Clear-Site-Data`, Header.Recurring] =
    Header.createRendered(
      ci"Clear-Site-Data",
      _.values,
      parse
    )

  implicit val headerSemigroupInstance: Semigroup[`Clear-Site-Data`] =
    (a, b) => `Clear-Site-Data`(a.values.concatNel(b.values))
}

final case class `Clear-Site-Data`(values: NonEmptyList[SiteData])
