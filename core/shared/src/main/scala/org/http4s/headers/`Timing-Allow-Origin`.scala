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

package org.http4s.headers

import cats.Semigroup
import cats.data.NonEmptyList
import org.http4s.Header
import org.http4s.ParseResult
import org.http4s.internal.parsing.CommonRules
import org.typelevel.ci._

object `Timing-Allow-Origin` {
  def apply(head: CIString, tail: CIString*): `Timing-Allow-Origin` =
    apply(NonEmptyList(head, tail.toList))

  private[http4s] def parse(s: String): ParseResult[`Timing-Allow-Origin`] =
    ParseResult.fromParser(parser, "Invalid Timing-Allow-Origin headers")(s)

  private[http4s] val parser =
    CommonRules
      .headerRep1(CommonRules.token.map(CIString(_)))
      .map(`Timing-Allow-Origin`(_))

  implicit val headerInstance: Header[`Timing-Allow-Origin`, Header.Recurring] =
    Header.createRendered(
      ci"Timing-Allow-Origin",
      _.values,
      parse,
    )

  implicit val timingAllowOriginSemigroup: Semigroup[`Timing-Allow-Origin`] =
    (a, b) => `Timing-Allow-Origin`(a.values.concatNel(b.values))
}

final case class `Timing-Allow-Origin`(values: NonEmptyList[CIString])
