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
import org.http4s.internal.parsing.AuthRules
import org.typelevel.ci._

object `Proxy-Authenticate` {

  def apply(head: Challenge, tail: Challenge*): `Proxy-Authenticate` =
    apply(NonEmptyList(head, tail.toList))

  private[http4s] val parser: Parser[`Proxy-Authenticate`] =
    AuthRules.challenges.map(`Proxy-Authenticate`.apply)

  def parse(s: String): ParseResult[`Proxy-Authenticate`] =
    ParseResult.fromParser(parser, "Invalid Proxy-Authenticate header")(s)

  implicit val headerInstance: Header[`Proxy-Authenticate`, Header.Recurring] =
    Header.createRendered(
      ci"Proxy-Authenticate",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[`Proxy-Authenticate`] =
    (a, b) => `Proxy-Authenticate`(a.values.concatNel(b.values))
}

/** {{{
  *   The "Proxy-Authenticate" header field consists of at least one
  *   challenge that indicates the authentication scheme(s) and parameters
  *   applicable to the proxy for this effective request URI...
  * }}}
  * From [[https://datatracker.ietf.org/doc/html/rfc7235#section-4.3 RFC-7235]]
  */
final case class `Proxy-Authenticate`(values: NonEmptyList[Challenge])
