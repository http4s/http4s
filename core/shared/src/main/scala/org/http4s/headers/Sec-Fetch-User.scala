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

import cats.parse.Parser
import org.typelevel.ci._

// https://w3c.github.io/webappsec-fetch-metadata/#sec-fetch-user-header

abstract class `Sec-Fetch-User` private[headers] (val value: String)

object `Sec-Fetch-User` {
  case object `?1` extends `Sec-Fetch-User`("?1")

  private val parser: Parser[`Sec-Fetch-User`] =
    Parser.string("?1").as(`?1`)

  def parse(s: String): ParseResult[`Sec-Fetch-User`] =
    ParseResult.fromParser(parser, "Invalid Sec-Fetch-User header")(s)

  def apply(value: `Sec-Fetch-User`): `Sec-Fetch-User` =
    value

  implicit val headerInstance: Header[`Sec-Fetch-User`, Header.Single] =
    Header.createRendered(
      ci"Sec-Fetch-User",
      _.value,
      parse,
    )
}
