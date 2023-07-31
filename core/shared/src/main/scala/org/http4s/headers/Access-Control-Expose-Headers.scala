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

object `Access-Control-Expose-Headers` {

  def apply(head: CIString, tail: CIString*): `Access-Control-Expose-Headers` =
    apply(NonEmptyList(head, tail.toList))

  def parse(s: String): ParseResult[`Access-Control-Expose-Headers`] =
    ParseResult.fromParser(parser, "Invalid Access-Control-Allow-Headers header")(s)

  private[http4s] val parser =
    CommonRules
      .headerRep1(CommonRules.token.map(CIString(_)))
      .map(`Access-Control-Expose-Headers`(_))

  implicit val headerInstance: Header[`Access-Control-Expose-Headers`, Header.Recurring] =
    Header.createRendered(
      ci"Access-Control-Expose-Headers",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[`Access-Control-Expose-Headers`] =
    (a, b) => `Access-Control-Expose-Headers`(a.values.concatNel(b.values))
}

final case class `Access-Control-Expose-Headers`(values: NonEmptyList[CIString])
