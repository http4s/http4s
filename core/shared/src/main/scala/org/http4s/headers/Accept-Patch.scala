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
import org.typelevel.ci._

object `Accept-Patch` {
  def apply(head: MediaType, tail: MediaType*): `Accept-Patch` =
    apply(NonEmptyList(head, tail.toList))

  def parse(s: String): ParseResult[`Accept-Patch`] =
    ParseResult.fromParser(parser, "Invalid Accept-Patch header")(s)

  private[http4s] val parser =
    CommonRules.headerRep1(MediaType.parser).map(`Accept-Patch`(_))

  implicit val headerInstance: Header[`Accept-Patch`, Header.Recurring] =
    Header.createRendered(
      ci"Accept-Patch",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[`Accept-Patch`] =
    (a, b) => `Accept-Patch`(a.values.concatNel(b.values))
}

// see https://datatracker.ietf.org/doc/html/rfc5789#section-3.1
final case class `Accept-Patch`(values: NonEmptyList[MediaType])
